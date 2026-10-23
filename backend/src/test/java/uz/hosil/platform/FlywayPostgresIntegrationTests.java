package uz.hosil.platform;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test running against a real PostgreSQL 16 container via Testcontainers.
 * Verifies that all resolved Flyway migrations apply cleanly against PostgreSQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hosil_test_db")
            .withUsername("hosil_test")
            .withPassword("hosil_test_pass");

    @Test
    void shouldApplyAllMigrationsAgainstPostgreSql16() throws Exception {
        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");

        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        int migrationsExecuted = flyway.migrate().migrationsExecuted;

        MigrationInfo[] allResolved = flyway.info().all();
        MigrationInfo[] appliedMigrations = flyway.info().applied();

        assertTrue(allResolved.length >= 20,
                "Should have discovered at least 20 Flyway migrations (V1 through V20), found: " + allResolved.length);
        assertEquals(allResolved.length, appliedMigrations.length,
                "All resolved migrations must be successfully applied to PostgreSQL");
        assertEquals(allResolved.length, migrationsExecuted,
                "Number of executed migrations on clean database must match total resolved migrations count");

        for (MigrationInfo info : appliedMigrations) {
            assertTrue(info.getState().isApplied(),
                    "Migration " + info.getScript() + " (version " + info.getVersion() + ") must be in APPLIED state");
        }

        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "Current migration must not be null after migration");
        MigrationInfo latestResolved = allResolved[allResolved.length - 1];
        assertEquals(latestResolved.getVersion(), current.getVersion(),
                "Current applied schema version must match the latest resolved migration version (" + latestResolved.getVersion() + ")");
        assertTrue(current.getState().isApplied(), "Current migration state must be APPLIED");

        // Verify key tables exist and UUID generation works
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Test inserting and reading from users table
            stmt.execute("INSERT INTO users (phone_number, preferred_language) VALUES ('+998901234567', 'uz');");
            try (ResultSet rs = stmt.executeQuery("SELECT id, phone_number, status FROM users WHERE phone_number = '+998901234567';")) {
                assertTrue(rs.next());
                assertNotNull(rs.getObject("id"));
                assertEquals("+998901234567", rs.getString("phone_number"));
                assertEquals("ACTIVE", rs.getString("status"));
            }

            // Verify baseline seeded categories exist
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM categories;")) {
                assertTrue(rs.next());
                assertEquals(4, rs.getInt(1), "Should have seeded 4 baseline categories");
            }

            // Verify baseline seeded units of measure exist
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM units_of_measure;")) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1), "Should have seeded 5 baseline units of measure");
            }

            // Verify category_allowed_units has seeded entries for HARVEST, FERTILIZER, SEEDS, MACHINERY
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM category_allowed_units;")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 10, "Should have seeded category allowed units across all baseline categories");
            }
        }
    }
}
