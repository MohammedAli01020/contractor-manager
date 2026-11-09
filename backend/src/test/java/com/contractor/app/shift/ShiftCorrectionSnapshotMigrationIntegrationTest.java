package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

/**
 * Verifies WF-08A/WF-08B migrations against an upgraded schema containing pre-WF-08A
 * correction audit rows. The disposable container is isolated from application data.
 */
class ShiftCorrectionSnapshotMigrationIntegrationTest {

    private static final MySQLContainer<?> MYSQL;

    static {
        MySQLContainer<?> container = null;
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("shift_correction_snapshot_migration_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--log-bin-trust-function-creators=1");
            container.start();
        }
        MYSQL = container;
    }

    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(MYSQL != null, "Docker unavailable: migration integration test skipped");
    }

    @Test
    void wf08aAndWf08bPreserveHistoricalCorrectionRowsAndInstallMeasuredSnapshotGuards() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("46"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(
                org.springframework.jdbc.datasource.DriverManagerDataSource.class.cast(
                        new org.springframework.jdbc.datasource.DriverManagerDataSource(
                                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())));

        long firstTenantId = insertTenant(jdbc, "Legacy correction tenant one");
        long secondTenantId = insertTenant(jdbc, "Legacy correction tenant two");
        insertLegacyCorrection(jdbc, firstTenantId, "legacy-correction-one");
        insertLegacyCorrection(jdbc, secondTenantId, "legacy-correction-two");

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForList(
                "SELECT tenant_id, idempotency_key, old_compensation_basis, new_compensation_basis, "
                        + "old_compensation_rate, new_compensation_rate, "
                        + "old_earning_workforce_ledger_entry_id, reversed_earning_workforce_ledger_entry_id, "
                        + "replacement_earning_workforce_ledger_entry_id "
                        + "FROM shift_corrections ORDER BY tenant_id"))
                .hasSize(2)
                .allSatisfy(row -> {
                    assertThat(((Number) row.get("tenant_id")).longValue()).isIn(firstTenantId, secondTenantId);
                    assertThat(row.get("old_compensation_basis")).isNull();
                    assertThat(row.get("new_compensation_basis")).isNull();
                    assertThat(row.get("old_compensation_rate")).isNull();
                    assertThat(row.get("new_compensation_rate")).isNull();
                    // V59 must not rewrite immutable historical correction audit rows.
                    assertThat(row.get("old_earning_workforce_ledger_entry_id")).isNull();
                    assertThat(row.get("reversed_earning_workforce_ledger_entry_id")).isNull();
                    assertThat(row.get("replacement_earning_workforce_ledger_entry_id")).isNull();
                });

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                        + "WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME IN "
                        + "('trg_shift_corrections_compensation_snapshot_insert', "
                        + "'trg_shift_corrections_compensation_snapshot_update', "
                        + "'trg_shifts_protect_completed_compensation')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND idempotency_key=?",
                Integer.class, firstTenantId, "legacy-correction-one")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shift_corrections WHERE tenant_id=? AND idempotency_key=?",
                Integer.class, secondTenantId, "legacy-correction-two")).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version='51' AND success=1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TRIGGERS "
                        + "WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME IN "
                        + "('trg_shift_corrections_workforce_refs_insert', "
                        + "'trg_shift_corrections_workforce_refs_guard')",
                Integer.class)).isEqualTo(2);

        // Every canonical reference is protected by a composite tenant+row FK.
        // Checking both FK columns prevents a valid row ID from another tenant
        // from being accepted merely because the numeric ID exists.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT CONSTRAINT_NAME) FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shift_corrections' "
                        + "AND REFERENCED_TABLE_NAME='workforce_ledger_entries' "
                        + "AND CONSTRAINT_NAME IN ('fk_shift_correction_old_workforce_ledger', "
                        + "'fk_shift_correction_reversed_workforce_ledger', "
                        + "'fk_shift_correction_replacement_workforce_ledger')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shift_corrections' "
                        + "AND REFERENCED_TABLE_NAME='workforce_ledger_entries' "
                        + "AND REFERENCED_COLUMN_NAME IN ('tenant_id','id') "
                        + "AND CONSTRAINT_NAME IN ('fk_shift_correction_old_workforce_ledger', "
                        + "'fk_shift_correction_reversed_workforce_ledger', "
                        + "'fk_shift_correction_replacement_workforce_ledger')",
                Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='workforce_ledger_entries' "
                        + "AND INDEX_NAME='uq_workforce_ledger_tenant_id' AND NON_UNIQUE=0 "
                        + "AND COLUMN_NAME IN ('tenant_id','id')",
                Integer.class)).isEqualTo(2);

        // Assert the exact local-to-referenced column mapping for every composite FK:
        // correction tenant_id -> ledger tenant_id, and each canonical ref -> ledger id.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='shift_corrections' "
                        + "AND REFERENCED_TABLE_NAME='workforce_ledger_entries' "
                        + "AND CONSTRAINT_NAME IN ('fk_shift_correction_old_workforce_ledger', "
                        + "'fk_shift_correction_reversed_workforce_ledger', "
                        + "'fk_shift_correction_replacement_workforce_ledger') "
                        + "AND ((COLUMN_NAME='tenant_id' AND REFERENCED_COLUMN_NAME='tenant_id') "
                        + "OR (COLUMN_NAME IN ('old_earning_workforce_ledger_entry_id', "
                        + "'reversed_earning_workforce_ledger_entry_id', "
                        + "'replacement_earning_workforce_ledger_entry_id') "
                        + "AND REFERENCED_COLUMN_NAME='id'))",
                Integer.class)).isEqualTo(6);

        // Prove the composite FK rejects a reference whose numeric ledger ID exists
        // but belongs to another tenant. Disable only the snapshot INSERT trigger in
        // this disposable schema so this test isolates the FK itself; V59's workforce
        // INSERT trigger remains active and accepts the zero-wage/no-old-ref snapshot.
        seedWorkforceLedgerRowForTenant(jdbc, firstTenantId, 9000001L);
        jdbc.execute("DROP TRIGGER trg_shift_corrections_compensation_snapshot_insert");
        assertThatThrownBy(() -> insertCorrectionWithForeignTenantLedgerReference(
                jdbc, secondTenantId, 9000001L))
                .hasMessageContaining("fk_shift_correction_replacement_workforce_ledger");
    }

    private static void seedWorkforceLedgerRowForTenant(JdbcTemplate jdbc, long tenantId, long ledgerId) {
        jdbc.execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                statement.execute(
                        "INSERT INTO workforce_ledger_entries (id, tenant_id, workforce_member_id, "
                                + "transaction_type, transaction_date, amount, balance_effect, description, "
                                + "client_request_key, shift_id, journal_entry_id, created_by_user_id, "
                                + "created_at, legacy_source_entry_id, legacy_driver_id) VALUES ("
                                + ledgerId + ", " + tenantId + ", 1, 'EARNING', '2025-01-01', 100.00, "
                                + "100.00, 'FK isolation fixture', 'fk-isolation-" + tenantId
                                + "', 9001, 9001, 1, UTC_TIMESTAMP(6), NULL, NULL)");
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            return null;
        });
    }

    private static void insertCorrectionWithForeignTenantLedgerReference(
            JdbcTemplate jdbc, long correctionTenantId, long foreignLedgerId) {
        jdbc.update(
                "INSERT INTO shift_corrections (tenant_id, shift_id, idempotency_key, reason, status, "
                        + "actor_user_id, expected_shift_version, old_shift_date, new_shift_date, "
                        + "old_shift_type, new_shift_type, old_site_id, new_site_id, old_driver_id, "
                        + "new_driver_id, old_equipment_id, new_equipment_id, old_driver_wage, "
                        + "new_driver_wage, old_price, new_price, old_equipment_operating_cost, "
                        + "new_equipment_operating_cost, replacement_earning_workforce_ledger_entry_id, "
                        + "created_at, updated_at) VALUES (?, 9001, ?, 'cross-tenant FK test', 'APPLYING', "
                        + "7001, 3, '2025-01-01', '2025-01-01', 'MORNING', 'MORNING', 11, 11, "
                        + "21, 21, 31, 31, 0.00, 0.00, 500.00, 500.00, 25.00, 25.00, ?, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                correctionTenantId, "cross-tenant-fk-" + correctionTenantId, foreignLedgerId);
    }

    private static long insertTenant(JdbcTemplate jdbc, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tenants (name, status) VALUES (?, 'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a tenant ID");
        }
        return key.longValue();
    }

    private static void insertLegacyCorrection(JdbcTemplate jdbc, long tenantId, String key) {
        jdbc.update(
                "INSERT INTO shift_corrections (tenant_id, shift_id, idempotency_key, reason, status, "
                        + "actor_user_id, expected_shift_version, old_shift_date, new_shift_date, "
                        + "old_shift_type, new_shift_type, old_site_id, new_site_id, old_driver_id, "
                        + "new_driver_id, old_equipment_id, new_equipment_id, old_driver_wage, "
                        + "new_driver_wage, old_price, new_price, old_equipment_operating_cost, "
                        + "new_equipment_operating_cost, created_at, updated_at) "
                        + "VALUES (?, 9001, ?, 'legacy correction', 'APPLIED', 7001, 3, "
                        + "'2025-01-01', '2025-01-01', 'MORNING', 'MORNING', 11, 11, 21, 21, "
                        + "31, 31, 100.00, 120.00, 500.00, 500.00, 25.00, 25.00, "
                        + "UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                tenantId, key);
    }
}
