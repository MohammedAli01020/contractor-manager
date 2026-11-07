package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
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
        assertThat(row.get("active")).isEqualTo(true);
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
    void legacyDriverUpdatesKeepTheWorkforceIdentitySynchronized() throws Exception {
        String token = registerTenant("Workforce Sync", uniqueName("wfsync"))
                .path("accessToken").asText();
        long driverId = createDriver(token, "Before Update", "01000000002", "NID-002", "before");

        JsonNode current = body(getJson("/api/v1/drivers/" + driverId, token)
                .andExpect(status().isOk()));
        Map<String, Object> update = new HashMap<>();
        update.put("name", "After Update");
        update.put("workerType", current.path("workerType").asText());
        update.put("phone", "01000000003");
        update.put("nationalId", "NID-003");
        update.put("notes", "after");
        update.put("morningRate", current.path("morningRate").decimalValue());
        update.put("eveningRate", current.path("eveningRate").decimalValue());
        update.put("nightRate", current.path("nightRate").decimalValue());
        update.put("active", false);
        update.put("version", current.path("version").asLong());

        putJson("/api/v1/drivers/" + driverId, update, token)
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT wm.full_name, wm.phone, wm.national_id, wm.notes, wm.active, "
                        + "d.workforce_member_id "
                        + "FROM drivers d JOIN workforce_members wm "
                        + "ON wm.id = d.workforce_member_id AND wm.tenant_id = d.tenant_id "
                        + "WHERE d.id = ?",
                driverId);
        assertThat(row.get("full_name")).isEqualTo("After Update");
        assertThat(row.get("phone")).isEqualTo("01000000003");
        assertThat(row.get("national_id")).isEqualTo("NID-003");
        assertThat(row.get("notes")).isEqualTo("after");
        assertThat(row.get("active")).isEqualTo(false);
    }

    @Test
    void editingOtherProfileFieldsPreservesNationalIdAcrossDriverAndWorkforceIdentity() throws Exception {
        String token = registerTenant("Workforce National ID Preservation", uniqueName("wfnid"))
                .path("accessToken").asText();
        long driverId = createDriver(token, "National ID Worker", "01000000004",
                "NID-PRESERVE-004", "before");

        JsonNode list = body(getJson("/api/v1/drivers?page=0&size=100", token)
                .andExpect(status().isOk()));
        JsonNode listedDriver = list.path("items").findValue("id");
        assertThat(listedDriver).isNotNull();
        JsonNode listItem = null;
        for (JsonNode item : list.path("items")) {
            if (item.path("id").asLong() == driverId) {
                listItem = item;
                break;
            }
        }
        assertThat(listItem).isNotNull();
        assertThat(listItem.path("nationalId").isNull()).isTrue();

        JsonNode detail = body(getJson("/api/v1/drivers/" + driverId, token)
                .andExpect(status().isOk()));
        assertThat(detail.path("nationalId").asText()).isEqualTo("NID-PRESERVE-004");

        Map<String, Object> update = new HashMap<>();
        update.put("name", "National ID Worker Updated");
        update.put("workerType", detail.path("workerType").asText());
        update.put("phone", "01000000005");
        update.put("nationalId", detail.path("nationalId").asText());
        update.put("notes", "updated without changing identity number");
        update.put("morningRate", detail.path("morningRate").decimalValue());
        update.put("eveningRate", detail.path("eveningRate").decimalValue());
        update.put("nightRate", detail.path("nightRate").decimalValue());
        update.put("active", detail.path("active").asBoolean());
        update.put("version", detail.path("version").asLong());

        putJson("/api/v1/drivers/" + driverId, update, token)
                .andExpect(status().isOk());

        Map<String, Object> persisted = jdbc.queryForMap(
                "SELECT d.national_id AS driver_national_id, wm.national_id AS member_national_id "
                        + "FROM drivers d JOIN workforce_members wm "
                        + "ON wm.id = d.workforce_member_id AND wm.tenant_id = d.tenant_id "
                        + "WHERE d.id = ?",
                driverId);
        assertThat(persisted.get("driver_national_id")).isEqualTo("NID-PRESERVE-004");
        assertThat(persisted.get("member_national_id")).isEqualTo("NID-PRESERVE-004");
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
