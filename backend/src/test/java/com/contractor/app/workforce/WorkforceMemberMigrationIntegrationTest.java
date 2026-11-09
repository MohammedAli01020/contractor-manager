package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises V36 against a populated schema stopped at V35, not only an empty fresh database. */
@Testcontainers(disabledWithoutDocker = true)
class WorkforceMemberMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("workforce_migration_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1");

    @Test
    void v36BackfillsExistingDriverIdentityWithoutChangingLegacyDriverId() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("35"))
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        long tenantId = insertAndReturnId(jdbc,
                "INSERT INTO tenants (name, status) VALUES (?, 'ACTIVE')",
                "Legacy workforce tenant");

        long driverId = insertAndReturnId(jdbc,
                "INSERT INTO drivers (tenant_id, name, phone, national_id, worker_type, "
                        + "morning_rate, evening_rate, night_rate, notes, active, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'WORKER', 100.00, 110.00, 120.00, ?, FALSE, 7, "
                        + "'2025-01-02 03:04:05.123456', '2025-02-03 04:05:06.654321')",
                tenantId, "Legacy Worker", "01012345678", "NID-LEGACY", "legacy notes");

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT d.id AS driver_id, d.workforce_member_id, d.tenant_id AS driver_tenant_id, "
                        + "wm.id AS member_id, wm.tenant_id AS member_tenant_id, wm.full_name, wm.phone, "
                        + "wm.national_id, wm.notes, wm.active, wm.version, wm.created_at, wm.updated_at "
                        + "FROM drivers d JOIN workforce_members wm "
                        + "ON wm.id = d.workforce_member_id AND wm.tenant_id = d.tenant_id "
                        + "WHERE d.id = ?",
                driverId);

        assertThat(((Number) row.get("driver_id")).longValue()).isEqualTo(driverId);
        assertThat(((Number) row.get("workforce_member_id")).longValue())
                .isEqualTo(((Number) row.get("member_id")).longValue());
        assertThat(((Number) row.get("driver_tenant_id")).longValue()).isEqualTo(tenantId);
        assertThat(((Number) row.get("member_tenant_id")).longValue()).isEqualTo(tenantId);
        assertThat(row.get("full_name")).isEqualTo("Legacy Worker");
        assertThat(row.get("phone")).isEqualTo("01012345678");
        assertThat(row.get("national_id")).isEqualTo("NID-LEGACY");
        assertThat(row.get("notes")).isEqualTo("legacy notes");
        assertThat(row.get("active")).isEqualTo(false);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(7L);
        assertThat(row.get("created_at"))
                .isEqualTo(LocalDateTime.of(2025, 1, 2, 3, 4, 5, 123456000));
        assertThat(row.get("updated_at"))
                .isEqualTo(LocalDateTime.of(2025, 2, 3, 4, 5, 6, 654321000));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_members WHERE tenant_id = ?",
                Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_financial_locks "
                        + "WHERE tenant_id = ? AND workforce_member_id = ?",
                Integer.class, tenantId, ((Number) row.get("member_id")).longValue())).isEqualTo(1);
    }

    private long insertAndReturnId(JdbcTemplate jdbc, String sql, Object... values) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator creator = connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            return statement;
        };
        jdbc.update(creator, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a generated key.");
        }
        return key.longValue();
    }
}
