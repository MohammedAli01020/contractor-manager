package com.contractor.app.shift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
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
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("53")).load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));

        long tenantId = insertId(jdbc, "INSERT INTO tenants (name) VALUES ('WF12C migration tenant')");
        long memberId = insertId(jdbc, "INSERT INTO workforce_members (tenant_id, full_name, active) "
                + "VALUES (" + tenantId + ", 'Legacy driver identity', TRUE)");
        long secondMemberId = insertId(jdbc, "INSERT INTO workforce_members (tenant_id, full_name, active) "
                + "VALUES (" + tenantId + ", 'Different identity', TRUE)");
        long driverId = insertId(jdbc, "INSERT INTO drivers (tenant_id, workforce_member_id, name, worker_type) "
                + "VALUES (" + tenantId + ", " + memberId + ", 'Legacy driver', 'DRIVER')");
        long siteId = insertId(jdbc, "INSERT INTO sites (tenant_id, name) VALUES (" + tenantId + ", 'Migration site')");
        long equipmentTypeId = insertId(jdbc, "INSERT INTO equipment_types (tenant_id, name) "
                + "VALUES (" + tenantId + ", 'Migration equipment type')");
        long equipmentId = insertId(jdbc, "INSERT INTO equipment (tenant_id, code, equipment_type_id) VALUES ("
                + tenantId + ", 'WF12C-EQ', " + equipmentTypeId + ")");
        long shiftId = insertId(jdbc, "INSERT INTO shifts (tenant_id, shift_date, shift_type, status, "
                + "finished_at, started_at, site_id, driver_id, equipment_id, wage_source, "
                + "compensation_component_code, compensation_component_name, compensation_basis, "
                + "compensation_rate, compensation_calculated_amount) VALUES (" + tenantId
                + ", '2026-01-10', 'MORNING', 'COMPLETED', '2026-01-10 08:00:00', "
                + "'2026-01-10 07:00:00', " + siteId + ", " + driverId + ", " + equipmentId
                + ", 'HISTORICAL_SHIFT_SNAPSHOT', 'HISTORICAL_SHIFT_WAGE', 'Historical recorded shift wage', "
                + "'SHIFT', 0.00, 0.00)");

        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT workforce_member_id FROM shifts WHERE id = ?",
                Long.class, shiftId)).isEqualTo(memberId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS "
                + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME IN "
                + "('fk_shifts_workforce_member', 'fk_shifts_driver_workforce_identity')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'drivers' "
                + "AND INDEX_NAME = 'uq_drivers_id_tenant_workforce_member'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'drivers' "
                + "AND INDEX_NAME = 'uq_drivers_id_tenant_workforce_member'",
                Integer.class)).isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO shifts (tenant_id, shift_date, shift_type, site_id, "
                + "driver_id, workforce_member_id, equipment_id) VALUES (" + tenantId
                + ", '2026-01-11', 'MORNING', " + siteId + ", " + driverId + ", "
                + secondMemberId + ", " + equipmentId + ")"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static long insertId(JdbcTemplate jdbc, String sql) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS), keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a generated ID");
        }
        return key.longValue();
    }
}
