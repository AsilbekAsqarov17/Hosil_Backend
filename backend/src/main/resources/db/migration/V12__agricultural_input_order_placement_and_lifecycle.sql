-- =============================================================================
-- Migration: V12__agricultural_input_order_placement_and_lifecycle.sql
-- Description: Extends orders, order_items, inventory_reservations, and seller_offers
--              to support agricultural-input orders with temporary reservations
--              and full inventory conservation (DEC-005, DEC-007, DEC-010, MKT-003, MKT-010).
-- Source of Truth: docs/04_domain_model.md §4.3 (Table 20), §4.4 (Tables 21-23),
--                  DEC-005, DEC-007, DEC-010
-- =============================================================================

-- 1. Extend orders table to support input checkout without requiring a harvest listing
ALTER TABLE orders ALTER COLUMN harvest_listing_id DROP NOT NULL;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_listing_flow;
ALTER TABLE orders ADD CONSTRAINT chk_orders_listing_flow CHECK (
    (order_flow_type = 'HARVEST_PURCHASE_REQUEST' AND harvest_listing_id IS NOT NULL) OR
    (order_flow_type = 'INPUT_DIRECT_CHECKOUT' AND harvest_listing_id IS NULL)
);

-- 2. Extend order_items table to link to either harvest_listings or seller_offers
ALTER TABLE order_items ALTER COLUMN harvest_listing_id DROP NOT NULL;
ALTER TABLE order_items ALTER COLUMN crop_type DROP NOT NULL;

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS seller_offer_id UUID REFERENCES seller_offers(id) ON DELETE RESTRICT;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS catalog_product_id UUID REFERENCES catalog_products(id) ON DELETE RESTRICT;

ALTER TABLE order_items DROP CONSTRAINT IF EXISTS chk_order_items_source;
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_source CHECK (
    (harvest_listing_id IS NOT NULL AND seller_offer_id IS NULL) OR
    (harvest_listing_id IS NULL AND seller_offer_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_order_items_seller_offer ON order_items(seller_offer_id) WHERE seller_offer_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_order_items_catalog_product ON order_items(catalog_product_id) WHERE catalog_product_id IS NOT NULL;

-- 3. Extend inventory_reservations table to support seller_offers reservations
ALTER TABLE inventory_reservations ALTER COLUMN harvest_listing_id DROP NOT NULL;

ALTER TABLE inventory_reservations ADD COLUMN IF NOT EXISTS seller_offer_id UUID REFERENCES seller_offers(id) ON DELETE RESTRICT;

ALTER TABLE inventory_reservations DROP CONSTRAINT IF EXISTS chk_inv_reservations_target;
ALTER TABLE inventory_reservations ADD CONSTRAINT chk_inv_reservations_target CHECK (
    (harvest_listing_id IS NOT NULL AND seller_offer_id IS NULL) OR
    (harvest_listing_id IS NULL AND seller_offer_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_inventory_reservations_seller_offer ON inventory_reservations(seller_offer_id) WHERE seller_offer_id IS NOT NULL;

-- 4. Extend seller_offers table with sold_quantity for strict quantity conservation
ALTER TABLE seller_offers ADD COLUMN IF NOT EXISTS sold_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0;

ALTER TABLE seller_offers DROP CONSTRAINT IF EXISTS chk_seller_offers_sold;
ALTER TABLE seller_offers ADD CONSTRAINT chk_seller_offers_sold CHECK (sold_quantity >= 0);
