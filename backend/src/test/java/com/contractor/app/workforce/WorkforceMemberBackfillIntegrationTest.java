package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** MySQL integration coverage for the additive workforce identity migration. */
class WorkforceMemberBackfillIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void newlyCreatedLegacyDriverHasExactlyOneMatchingWorkforceIdentity() throws Exception {
        String token = registerTenant("Workforce Backfill", uniqueName("wfowner"))
                .path("accessToken").asText();

        long driverId = createDriver(token, "Workforce Driver", "01000000001", "NID-001", "profile note");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT d.workforce_member_id, wm.id AS member_id, wm.tenant_id, wm.full_name, "
                        + "wm.phone, wm.national_id, wm.notes, wm.active, wm.version, "
                        + "d.tenant_id AS driver_tenant_id "
                        + "FROM drivers d JOIN workforce_members wm "
                        + "ON wm.id = d.workforce_member_id AND wm.tenant_id = d.tenant_id "
                        + "WHERE d.id = ?",
                driverId);

        assertThat(((Number) row.get("workforce_member_id")).longValue())
                .isEqualTo(((Number) row.get("member_id")).longValue());
        assertThat(((Number) row.get("tenant_id")).longValue())
                .isEqualTo(((Number) row.get("driver_tenant_id")).longValue());
        assertThat(row.get("full_name")).isEqualTo("Workforce Driver");
        assertThat(row.get("phone")).isEqualTo("01000000001");
        assertThat(row.get("national_id")).isEqualTo("NID-001");
        assertThat(row.get("notes")).isEqualTo("profile note");
        assertThat(((Number) row.get("active")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("version")).longValue()).isZero();

        Integer driverCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE id = ?", Integer.class, driverId);
        Integer mappingCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_members WHERE id = ?",
                Integer.class, row.get("member_id"));
        assertThat(driverCount).isEqualTo(1);
        assertThat(mappingCount).isEqualTo(1);
    }

    @Test
    void databaseRejectsCrossTenantAndDuplicateDriverMappings() throws Exception {
        String tokenA = registerTenant("Workforce Tenant A", uniqueName("wfa"))
                .path("accessToken").asText();
        String tokenB = registerTenant("Workforce Tenant B", uniqueName("wfb"))
                .path("accessToken").asText();

        long driverA = createDriver(tokenA, "Tenant A Driver", null, null, null);
        long secondDriverA = createDriver(tokenA, "Tenant A Second Driver", null, null, null);
        long driverB = createDriver(tokenB, "Tenant B Driver", null, null, null);

        long memberA = memberIdFor(driverA);
        long memberB = memberIdFor(driverB);
        long tenantA = tenantIdFor(driverA);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE drivers SET workforce_member_id = ? WHERE id = ?",
                memberB, driverA))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE drivers SET workforce_member_id = ? WHERE id = ?",
                memberA, secondDriverA))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM drivers WHERE tenant_id = ? AND workforce_member_id = ?",
                Integer.class, tenantA, memberA)).isEqualTo(1);
    }

    private long createDriver(String token, String name, String phone, String nationalId, String notes)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("workerType", "DRIVER");
        request.put("phone", phone);
        request.put("nationalId", nationalId);
        request.put("notes", notes);
        request.put("morningRate", new BigDecimal("100.00"));
        request.put("eveningRate", new BigDecimal("110.00"));
        request.put("nightRate", new BigDecimal("120.00"));
        return body(postJson("/api/v1/drivers", request, token)
                .andExpect(status().isCreated())).path("id").asLong();
    }

    private long memberIdFor(long driverId) {
        return jdbc.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ?",
                Long.class, driverId);
    }

    private long tenantIdFor(long driverId) {
        return jdbc.queryForObject(
                "SELECT tenant_id FROM drivers WHERE id = ?",
                Long.class, driverId);
    }
}
