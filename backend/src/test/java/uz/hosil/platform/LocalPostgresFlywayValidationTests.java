package uz.hosil.platform;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates connection, schema reconciliation, and Flyway migrations against the running local PostgreSQL 16 container.
 */
class LocalPostgresFlywayValidationTests {

    private static final String JDBC_URL = "jdbc:postgresql://127.0.0.1:5433/hosil_db";
    private static final String DB_USER = "hosil_dev";
    private static final String DB_PASS = "hosil_dev_secret";

    @Test
    void shouldValidateAndMigrateAllAgainstLocalPostgres16() throws Exception {
        // 1. Configure and migrate Flyway forward
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .load();

        MigrateResult migrateResult = flyway.migrate();
        ValidateResult validateResult = flyway.validateWithResult();
        assertTrue(validateResult.validationSuccessful, "Flyway validation failed: " + validateResult.getAllErrorMessages());

        // 2. Query direct JDBC connection and verify reconciled schema
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Check version is PostgreSQL 16
            try (ResultSet rs = stmt.executeQuery("SELECT version();")) {
                assertTrue(rs.next());
                String version = rs.getString(1);
                assertTrue(version.contains("PostgreSQL 16"), "Expected PostgreSQL 16 but got: " + version);
            }

            // Verify all 4 migrations are marked successful in flyway_schema_history
            List<String> appliedScripts = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT script, success FROM flyway_schema_history ORDER BY installed_rank;")) {
                while (rs.next()) {
                    appliedScripts.add(rs.getString("script"));
                    assertTrue(rs.getBoolean("success"), "Migration script failed: " + rs.getString("script"));
                }
            }

            assertTrue(appliedScripts.contains("V1__init_extensions_and_identity.sql"));
            assertTrue(appliedScripts.contains("V2__farm_core_schema.sql"));
            assertTrue(appliedScripts.contains("V3__marketplace_bridge_schema.sql"));
            assertTrue(appliedScripts.contains("V4__reconcile_canonical_domain_model.sql"));
            assertTrue(appliedScripts.contains("V5__reconcile_farm_core_schema.sql"));
            assertTrue(appliedScripts.contains("V6__reconcile_farm_activities_schema.sql"));
            assertTrue(appliedScripts.contains("V7__harvest_batches_and_market_publication.sql"));
            assertTrue(appliedScripts.contains("V8__harvest_listing_discovery_indexes.sql"));
            assertTrue(appliedScripts.contains("V9__buyer_inquiries_and_rfq_negotiation.sql"));
            assertTrue(appliedScripts.contains("V10__wholesale_order_placement_and_lifecycle.sql"));
            assertTrue(appliedScripts.contains("V11__agricultural_input_catalog_and_seller_offers.sql"));
            assertTrue(appliedScripts.contains("V12__agricultural_input_order_placement_and_lifecycle.sql"));
            assertTrue(appliedScripts.contains("V13__logistics_shipment_requests.sql"));
            assertTrue(appliedScripts.contains("V14__shipment_request_active_linkage_guards.sql"));
            assertTrue(appliedScripts.contains("V15__seed_category_allowed_units_and_harvest.sql"));

            // Verify auth_sessions exists and has last_active_at and is_revoked
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'auth_sessions';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("is_revoked"));
                assertTrue(cols.contains("last_active_at"));
                assertTrue(cols.contains("device_fingerprint"));
            }

            // Verify harvest_listings exists and has canonical fields
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'harvest_listings';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("crop_type"));
                assertTrue(cols.contains("allocated_quantity"));
                assertTrue(cols.contains("available_quantity"));
                assertTrue(cols.contains("reserved_quantity"));
                assertTrue(cols.contains("sold_quantity"));
                assertTrue(cols.contains("min_order_quantity"));
                assertTrue(cols.contains("step_quantity"));
                assertTrue(cols.contains("idempotency_key"));
            }

            // Verify harvest_batches has recorded_quantity and unallocated_quantity and crop_type
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'harvest_batches';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("recorded_quantity"));
                assertTrue(cols.contains("unallocated_quantity"));
                assertTrue(cols.contains("unit_code"));
                assertTrue(cols.contains("crop_type"));
                assertTrue(cols.contains("client_batch_id"));
                assertTrue(cols.contains("idempotency_key"));
                assertTrue(cols.contains("sync_version"));
                assertTrue(cols.contains("storage_location_notes"));
            }

            // Verify farm_memberships has status, MANAGER role, and permissions_mask
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'farm_memberships';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("status"));
                assertTrue(cols.contains("permissions_mask"));
            }

            // Verify farms has legal_type and total_area_ha
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'farms';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("legal_type"));
                assertTrue(cols.contains("total_area_ha"));
            }

            // Verify fields has notes
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'fields';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("notes"));
            }

            // Verify crop_cycles has crop_type, variety, and actual_harvest_date
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'crop_cycles';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("crop_type"));
                assertTrue(cols.contains("variety"));
                assertTrue(cols.contains("actual_harvest_date"));
            }

            // Verify farm_activities has activity_timestamp, recorded_by_user_id, sync_version, and client_mutation_id
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'farm_activities';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("activity_timestamp"));
                assertTrue(cols.contains("recorded_by_user_id"));
                assertTrue(cols.contains("sync_version"));
                assertTrue(cols.contains("client_mutation_id"));
            }

            // Verify harvest_allocations has allocated_at
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'harvest_allocations';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("allocated_at"));
            }

            // Verify listing_media table exists
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_name = 'listing_media';")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            // Verify V8 partial discovery index exists
            try (ResultSet rs = stmt.executeQuery("SELECT indexname FROM pg_indexes WHERE indexname = 'idx_harvest_listings_published_discovery';")) {
                assertTrue(rs.next(), "Discovery index idx_harvest_listings_published_discovery must exist");
            }

            // Verify V9 inquiries and inquiry_offers tables exist
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'inquiries';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("harvest_listing_id"));
                assertTrue(cols.contains("buyer_user_id"));
                assertTrue(cols.contains("seller_user_id"));
                assertTrue(cols.contains("status"));
                assertTrue(cols.contains("current_offer_id"));
                assertTrue(cols.contains("agreed_offer_id"));
                assertTrue(cols.contains("version"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'inquiry_offers';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("inquiry_id"));
                assertTrue(cols.contains("offer_number"));
                assertTrue(cols.contains("proposer_user_id"));
                assertTrue(cols.contains("proposer_role"));
                assertTrue(cols.contains("quantity"));
                assertTrue(cols.contains("unit_price"));
                assertTrue(cols.contains("status"));
            }

            // Verify V10 orders, order_items, order_status_history, inventory_reservations
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'orders';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("order_number"));
                assertTrue(cols.contains("buyer_user_id"));
                assertTrue(cols.contains("seller_user_id"));
                assertTrue(cols.contains("harvest_listing_id"));
                assertTrue(cols.contains("inquiry_id"));
                assertTrue(cols.contains("agreed_offer_id"));
                assertTrue(cols.contains("order_flow_type"));
                assertTrue(cols.contains("total_amount"));
                assertTrue(cols.contains("status"));
                assertTrue(cols.contains("delivery_type"));
                assertTrue(cols.contains("idempotency_key"));
                assertTrue(cols.contains("version"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'order_items';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("order_id"));
                assertTrue(cols.contains("harvest_listing_id"));
                assertTrue(cols.contains("seller_offer_id"));
                assertTrue(cols.contains("catalog_product_id"));
                assertTrue(cols.contains("item_snapshot_title"));
                assertTrue(cols.contains("crop_type"));
                assertTrue(cols.contains("quantity"));
                assertTrue(cols.contains("unit_code"));
                assertTrue(cols.contains("unit_price"));
                assertTrue(cols.contains("subtotal_amount"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'order_status_history';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("order_id"));
                assertTrue(cols.contains("from_status"));
                assertTrue(cols.contains("to_status"));
                assertTrue(cols.contains("actor_role"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'inventory_reservations';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("order_id"));
                assertTrue(cols.contains("harvest_listing_id"));
                assertTrue(cols.contains("seller_offer_id"));
                assertTrue(cols.contains("reserved_quantity"));
                assertTrue(cols.contains("unit_code"));
                assertTrue(cols.contains("expires_at"));
                assertTrue(cols.contains("status"));
            }

            // V11 & V15: category_allowed_units, catalog_products, seller_offers
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'category_allowed_units';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("category_id"));
                assertTrue(cols.contains("unit_code"));
            }

            // Verify V15 seeded category_allowed_units for HARVEST, FERTILIZER, SEEDS, MACHINERY
            try (ResultSet rs = stmt.executeQuery("""
                SELECT c.code, count(cau.unit_code)
                FROM categories c
                JOIN category_allowed_units cau ON c.id = cau.category_id
                GROUP BY c.code;
            """)) {
                int verifiedCategories = 0;
                while (rs.next()) {
                    String catCode = rs.getString(1);
                    int count = rs.getInt(2);
                    if ("HARVEST".equals(catCode)) {
                        assertEquals(4, count, "HARVEST must have 4 allowed units (KG, TON, PIECE, BAG_50KG)");
                        verifiedCategories++;
                    } else if ("FERTILIZER".equals(catCode)) {
                        assertEquals(4, count, "FERTILIZER must have 4 allowed units (KG, TON, BAG_50KG, LITER)");
                        verifiedCategories++;
                    } else if ("SEEDS".equals(catCode)) {
                        assertEquals(4, count, "SEEDS must have 4 allowed units (KG, TON, BAG_50KG, PIECE)");
                        verifiedCategories++;
                    } else if ("MACHINERY".equals(catCode)) {
                        assertEquals(1, count, "MACHINERY must have 1 allowed unit (PIECE)");
                        verifiedCategories++;
                    }
                }
                assertEquals(4, verifiedCategories, "All 4 baseline categories must have verified allowed units");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'catalog_products';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("category_id"));
                assertTrue(cols.contains("name_uz"));
                assertTrue(cols.contains("name_ru"));
                assertTrue(cols.contains("brand_name"));
                assertTrue(cols.contains("active_substance"));
                assertTrue(cols.contains("default_unit_code"));
                assertTrue(cols.contains("is_active"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'seller_offers';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("seller_user_id"));
                assertTrue(cols.contains("catalog_product_id"));
                assertTrue(cols.contains("unit_price"));
                assertTrue(cols.contains("currency"));
                assertTrue(cols.contains("unit_code"));
                assertTrue(cols.contains("available_quantity"));
                assertTrue(cols.contains("reserved_quantity"));
                assertTrue(cols.contains("sold_quantity"));
                assertTrue(cols.contains("min_order_quantity"));
                assertTrue(cols.contains("step_quantity"));
                assertTrue(cols.contains("status"));
            }

            // V13 & V14: carrier_profiles, shipment_requests, shipment_status_history
            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'carrier_profiles';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("user_id"));
                assertTrue(cols.contains("company_name"));
                assertTrue(cols.contains("license_number"));
                assertTrue(cols.contains("verification_status"));
                assertTrue(cols.contains("verified_at"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'shipment_requests';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("request_number"));
                assertTrue(cols.contains("requester_user_id"));
                assertTrue(cols.contains("order_id"));
                assertTrue(cols.contains("harvest_batch_id"));
                assertTrue(cols.contains("cargo_description"));
                assertTrue(cols.contains("cargo_weight_kg"));
                assertTrue(cols.contains("origin_region"));
                assertTrue(cols.contains("origin_district"));
                assertTrue(cols.contains("destination_region"));
                assertTrue(cols.contains("destination_district"));
                assertTrue(cols.contains("pickup_window_start"));
                assertTrue(cols.contains("pickup_window_end"));
                assertTrue(cols.contains("status"));
                assertTrue(cols.contains("assigned_carrier_user_id"));
                assertTrue(cols.contains("idempotency_key"));
            }

            try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name = 'shipment_status_history';")) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) cols.add(rs.getString(1));
                assertTrue(cols.contains("shipment_request_id"));
                assertTrue(cols.contains("from_status"));
                assertTrue(cols.contains("to_status"));
                assertTrue(cols.contains("actor_user_id"));
                assertTrue(cols.contains("actor_role"));
            }

            // V14 active partial unique indexes
            try (ResultSet rs = stmt.executeQuery("SELECT indexname FROM pg_indexes WHERE indexname = 'uq_shipment_requests_active_order';")) {
                assertTrue(rs.next(), "Partial unique index uq_shipment_requests_active_order must exist");
            }

            try (ResultSet rs = stmt.executeQuery("SELECT indexname FROM pg_indexes WHERE indexname = 'uq_shipment_requests_active_batch';")) {
                assertTrue(rs.next(), "Partial unique index uq_shipment_requests_active_batch must exist");
            }
        }
    }
}

