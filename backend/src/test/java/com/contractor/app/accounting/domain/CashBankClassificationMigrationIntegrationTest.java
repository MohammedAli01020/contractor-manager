package com.contractor.app.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Verifies V24 against a populated schema at V23, not only a fresh database.
 * The test uses an isolated disposable MySQL container and never targets an application database.
 */
class CashBankClassificationMigrationIntegrationTest {

    private static final MySQLContainer<?> MYSQL;

    static {
        MySQLContainer<?> container = null;
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("migration_test")
                    .withUsername("test")
                    .withPassword("test")
                    // Earlier migrations create triggers; this setting lets the disposable CI
                    // database create them while binary logging is enabled.
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
    void v24BackfillsOnlyExistingAssetCashAndBankAccountsWithoutChangingOtherData() throws SQLException {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(MYSQL.getJdbcUrl());
        dataSource.setUser(MYSQL.getUsername());
        dataSource.setPassword(MYSQL.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("23"))
                .load()
                .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long assetTenantId = insertTenant(jdbc, "Existing asset tenant");
        long liabilityTenantId = insertTenant(jdbc, "Existing liability tenant");

        insertAccount(jdbc, assetTenantId, "1000", "Existing cash", "ASSET");
        insertAccount(jdbc, assetTenantId, "1010", "Existing bank", "ASSET");
        insertAccount(jdbc, assetTenantId, "1100", "Receivables", "ASSET");
        insertAccount(jdbc, assetTenantId, "1300", "Equipment", "ASSET");
        insertAccount(jdbc, liabilityTenantId, "1000", "Legacy liability code", "LIABILITY");
        insertAccount(jdbc, liabilityTenantId, "1010", "Second tenant bank", "ASSET");

        List<Map<String, Object>> rowsBefore = jdbc.queryForList(
                "SELECT tenant_id, account_code, name, account_type, system_managed, active, version "
                        + "FROM chart_of_accounts ORDER BY tenant_id, account_code");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Boolean> classifications = new LinkedHashMap<>();
        jdbc.query("SELECT tenant_id, account_code, cash_bank_account FROM chart_of_accounts",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        classifications.put(key(rs), rs.getBoolean("cash_bank_account")));

        assertThat(classifications)
                .containsEntry(assetTenantId + ":1000", true)
                .containsEntry(assetTenantId + ":1010", true)
                .containsEntry(assetTenantId + ":1100", false)
                .containsEntry(assetTenantId + ":1300", false)
                .containsEntry(liabilityTenantId + ":1000", false)
                .containsEntry(liabilityTenantId + ":1010", true);

        List<Map<String, Object>> rowsAfter = jdbc.queryForList(
                "SELECT tenant_id, account_code, name, account_type, system_managed, active, version "
                        + "FROM chart_of_accounts ORDER BY tenant_id, account_code");
        assertThat(rowsAfter).isEqualTo(rowsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chart_of_accounts", Integer.class)).isEqualTo(6);
    }

    private static long insertTenant(JdbcTemplate jdbc, String name) {
        jdbc.update("INSERT INTO tenants (name, status) VALUES (?, 'ACTIVE')", name);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private static void insertAccount(
            JdbcTemplate jdbc, long tenantId, String code, String name, String accountType) {
        jdbc.update(
                "INSERT INTO chart_of_accounts "
                        + "(tenant_id, account_code, name, account_type, system_managed, active) "
                        + "VALUES (?, ?, ?, ?, FALSE, TRUE)",
                tenantId, code, name, accountType);
    }

    private static String key(ResultSet rs) throws SQLException {
        return rs.getLong("tenant_id") + ":" + rs.getString("account_code");
    }
}
