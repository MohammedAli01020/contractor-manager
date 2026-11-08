package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/** Verifies V54 backfills historical shifts and enforces the driver/workforce identity tuple. */
class ShiftWorkforceIdentityMigrationIntegrationTest {

    private static final MySQLContainer<?> MYSQL;

    static {
        MySQLContainer<?> container = null;
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("shift_workforce_identity_migration_test")
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
    void v54BackfillsHistoricalShiftAndRejectsMismatchedDriverWorkforceIdentity() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("53"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        long tenantId = jdbc.queryForObject(
                "INSERT INTO tenants (name) VALUES ('WF12C migration tenant')",
                Long.class);
        long memberId = jdbc.queryForObject(
                "INSERT INTO workforce_members (tenant_id, full_name, active) VALUES (?, 'Legacy driver identity', TRUE)",
                Long.class, tenantId);
        long secondMemberId = jdbc.queryForObject(
                "INSERT INTO workforce_members (tenant_id, full_name, active) VALUES (?, 'Different identity', TRUE)",
                Long.class, tenantId);
        long driverId = jdbc.queryForObject(
                "INSERT INTO drivers (tenant_id, workforce_member_id, name, worker_type) "
                        + "VALUES (?, ?, 'Legacy driver', 'DRIVER')",
                Long.class, tenantId, memberId);
        long siteId = jdbc.queryForObject(
                "INSERT INTO sites (tenant_id, name) VALUES (?, 'Migration site')",
                Long.class, tenantId);
        long equipmentTypeId = jdbc.queryForObject(
                "INSERT INTO equipment_types (tenant_id, name) VALUES (?, 'Migration equipment type')",
                Long.class, tenantId);
        long equipmentId = jdbc.queryForObject(
                "INSERT INTO equipment (tenant_id, code, equipment_type_id) VALUES (?, 'WF12C-EQ', ?)",
                Long.class, tenantId, equipmentTypeId);

        long shiftId = jdbc.queryForObject(
                "INSERT INTO shifts (tenant_id, shift_date, shift_type, site_id, driver_id, equipment_id) "
                        + "VALUES (?, '2026-01-10', 'MORNING', ?, ?, ?)",
                Long.class, tenantId, siteId, driverId, equipmentId);

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForObject(
                "SELECT workforce_member_id FROM shifts WHERE id = ?", Long.class, shiftId))
                .isEqualTo(memberId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                        + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN "
                        + "('fk_shifts_workforce_member', 'fk_shifts_driver_workforce_identity')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'drivers' "
                        + "AND INDEX_NAME = 'uq_drivers_id_tenant_workforce_member'",
                Integer.class)).isEqualTo(3);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO shifts (tenant_id, shift_date, shift_type, site_id, driver_id, "
                        + "workforce_member_id, equipment_id) VALUES (?, '2026-01-11', 'MORNING', ?, ?, ?, ?)",
                tenantId, siteId, driverId, secondMemberId, equipmentId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
