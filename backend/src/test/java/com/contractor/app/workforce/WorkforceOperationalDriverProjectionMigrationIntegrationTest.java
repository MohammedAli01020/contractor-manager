package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/** Verifies V53 repairs missing operational projections without changing existing driver data. */
class WorkforceOperationalDriverProjectionMigrationIntegrationTest {

    private static final MySQLContainer<?> MYSQL;

    static {
        MySQLContainer<?> container = null;
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("wf12b_projection_migration_test")
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
    void v53CreatesMissingOperationalProjectionAndPreservesExistingProjection() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("52")).load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long tenantId = insertTenant(jdbc);
        long driverRoleId = insertRole(jdbc, tenantId, "DRIVER");
        long workerRoleId = insertRole(jdbc, tenantId, "WORKER");
        long technicianRoleId = insertRole(jdbc, tenantId, "TECHNICIAN");

        long missingDriverMemberId = insertMember(jdbc, tenantId, "سعيد", true);
        insertAssignment(jdbc, tenantId, missingDriverMemberId, driverRoleId);

        long existingDriverMemberId = insertMember(jdbc, tenantId, "Existing Driver", true);
        insertAssignment(jdbc, tenantId, existingDriverMemberId, driverRoleId);
        long existingDriverId = insertDriver(jdbc, tenantId, existingDriverMemberId,
                "Existing Driver", "DRIVER", "UNAVAILABLE", "maintenance", 125.00, 135.00, 145.00);

        long technicianMemberId = insertMember(jdbc, tenantId, "Technician Only", true);
        insertAssignment(jdbc, tenantId, technicianMemberId, technicianRoleId);

        long inactiveMemberId = insertMember(jdbc, tenantId, "Inactive Driver", false);
        insertAssignment(jdbc, tenantId, inactiveMemberId, driverRoleId);

        long workerMemberId = insertMember(jdbc, tenantId, "Worker", true);
        insertAssignment(jdbc, tenantId, workerMemberId, workerRoleId);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        Map<String, Object> created = jdbc.queryForMap(
                "SELECT id, tenant_id, workforce_member_id, name, worker_type, active, "
                        + "morning_rate, evening_rate, night_rate FROM drivers "
                        + "WHERE tenant_id = ? AND workforce_member_id = ?",
                tenantId, missingDriverMemberId);
        assertThat(((Number) created.get("workforce_member_id")).longValue()).isEqualTo(missingDriverMemberId);
        assertThat(created.get("name")).isEqualTo("سعيد");
        assertThat(created.get("worker_type")).isEqualTo("DRIVER");
        assertThat(created.get("active")).isEqualTo(true);
        assertThat(((Number) created.get("morning_rate")).doubleValue()).isZero();
        assertThat(((Number) created.get("evening_rate")).doubleValue()).isZero();
        assertThat(((Number) created.get("night_rate")).doubleValue()).isZero();

        Map<String, Object> preserved = jdbc.queryForMap(
                "SELECT id, availability, unavailable_reason, morning_rate, evening_rate, night_rate "
                        + "FROM drivers WHERE id = ?", existingDriverId);
        assertThat(((Number) preserved.get("id")).longValue()).isEqualTo(existingDriverId);
        assertThat(preserved.get("availability")).isEqualTo("UNAVAILABLE");
        assertThat(preserved.get("unavailable_reason")).isEqualTo("maintenance");
        assertThat(((Number) preserved.get("morning_rate")).doubleValue()).isEqualTo(125.00);
        assertThat(((Number) preserved.get("evening_rate")).doubleValue()).isEqualTo(135.00);
        assertThat(((Number) preserved.get("night_rate")).doubleValue()).isEqualTo(145.00);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE tenant_id = ? AND workforce_member_id = ?",
                Integer.class, tenantId, existingDriverMemberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE tenant_id = ? AND workforce_member_id = ?",
                Integer.class, tenantId, technicianMemberId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE tenant_id = ? AND workforce_member_id = ?",
                Integer.class, tenantId, inactiveMemberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT worker_type FROM drivers WHERE tenant_id = ? AND workforce_member_id = ?",
                String.class, tenantId, workerMemberId)).isEqualTo("WORKER");
    }

    private static long insertTenant(JdbcTemplate jdbc) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(
                "INSERT INTO tenants (name) VALUES ('WF12B projection migration')",
                Statement.RETURN_GENERATED_KEYS), key);
        return key.getKey().longValue();
    }

    private static long insertRole(JdbcTemplate jdbc, long tenantId, String code) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO workforce_roles (tenant_id, code, name, system_defined, active) "
                            + "VALUES (?, ?, ?, TRUE, TRUE)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, code);
            statement.setString(3, code);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private static long insertMember(JdbcTemplate jdbc, long tenantId, String name, boolean active) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO workforce_members (tenant_id, full_name, active) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, name);
            statement.setBoolean(3, active);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private static void insertAssignment(JdbcTemplate jdbc, long tenantId, long memberId, long roleId) {
        jdbc.update("INSERT INTO workforce_assignments "
                        + "(tenant_id, workforce_member_id, workforce_role_id, effective_from, status) "
                        + "VALUES (?, ?, ?, '2026-01-01', 'ACTIVE')",
                tenantId, memberId, roleId);
    }

    private static long insertDriver(JdbcTemplate jdbc, long tenantId, long memberId, String name,
                                     String workerType, String availability, String unavailableReason,
                                     double morningRate, double eveningRate, double nightRate) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO drivers (tenant_id, workforce_member_id, name, worker_type, availability, "
                            + "unavailable_reason, morning_rate, evening_rate, night_rate) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setLong(2, memberId);
            statement.setString(3, name);
            statement.setString(4, workerType);
            statement.setString(5, availability);
            statement.setString(6, unavailableReason);
            statement.setDouble(7, morningRate);
            statement.setDouble(8, eveningRate);
            statement.setDouble(9, nightRate);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
