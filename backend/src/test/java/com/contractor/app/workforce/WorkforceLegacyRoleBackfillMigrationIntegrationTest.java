package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
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

/** Verifies WF12A legacy role backfill against a populated isolated V51 schema. */
class WorkforceLegacyRoleBackfillMigrationIntegrationTest {

    private static final MySQLContainer<?> MYSQL;

    static {
        MySQLContainer<?> container = null;
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("wf12a_migration_test")
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
    void v52BackfillsLegacyDriverAndWorkerRolesWithoutDuplicatingExistingAssignments() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("51")).load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long tenantId = insertTenant(jdbc);
        long driverRoleId = insertRole(jdbc, tenantId, "DRIVER");
        long workerRoleId = insertRole(jdbc, tenantId, "WORKER");
        long driverMemberId = insertMember(jdbc, tenantId, "Legacy Driver");
        long workerMemberId = insertMember(jdbc, tenantId, "Legacy Worker");
        long assignedMemberId = insertMember(jdbc, tenantId, "Already Assigned");
        insertDriver(jdbc, tenantId, driverMemberId, "DRIVER");
        insertDriver(jdbc, tenantId, workerMemberId, "WORKER");
        insertDriver(jdbc, tenantId, assignedMemberId, "DRIVER");
        jdbc.update("INSERT INTO workforce_assignments "
                        + "(tenant_id, workforce_member_id, workforce_role_id, effective_from, status) "
                        + "VALUES (?, ?, ?, '2026-01-01', 'ACTIVE')",
                tenantId, assignedMemberId, driverRoleId);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT workforce_member_id, workforce_role_id, effective_from, status "
                        + "FROM workforce_assignments WHERE tenant_id = ? ORDER BY workforce_member_id",
                tenantId);
        assertThat(rows).hasSize(3);
        assertThat(jdbc.queryForObject(
                "SELECT workforce_role_id FROM workforce_assignments WHERE workforce_member_id = ?",
                Long.class, driverMemberId)).isEqualTo(driverRoleId);
        assertThat(jdbc.queryForObject(
                "SELECT workforce_role_id FROM workforce_assignments WHERE workforce_member_id = ?",
                Long.class, workerMemberId)).isEqualTo(workerRoleId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_assignments WHERE workforce_member_id = ?",
                Integer.class, assignedMemberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_assignments WHERE tenant_id = ? AND notes = "
                        + "'Migrated from legacy drivers.worker_type during WF12A'",
                Integer.class, tenantId)).isEqualTo(2);
    }

    private static long insertTenant(JdbcTemplate jdbc) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO tenants (name) VALUES ('WF12A legacy backfill')",
                    Statement.RETURN_GENERATED_KEYS);
            return statement;
        }, key);
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

    private static long insertMember(JdbcTemplate jdbc, long tenantId, String name) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO workforce_members (tenant_id, full_name, active) VALUES (?, ?, TRUE)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setString(2, name);
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private static void insertDriver(JdbcTemplate jdbc, long tenantId, long memberId, String workerType) {
        jdbc.update("INSERT INTO drivers (tenant_id, workforce_member_id, name, worker_type) "
                        + "VALUES (?, ?, ?, ?)",
                tenantId, memberId, "Legacy " + workerType, workerType);
    }
}
