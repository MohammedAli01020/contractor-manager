package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Canonical identity API contract, tenant isolation, and legacy-driver synchronization. */
class WorkforceMemberCrudIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void adminCanCreateAndUpdateCanonicalPersonWithoutCreatingDriverRecord() throws Exception {
        String token = registerTenant("Canonical Workforce", uniqueName("canonicalwf"))
                .path("accessToken").asText();

        Map<String, Object> create = identity("Canonical Engineer", "01055550001", "NID-CAN-01");
        JsonNode created = body(postJson("/api/v1/workforce/members", create, token)
                .andExpect(status().isCreated()));

        long memberId = created.path("id").asLong();
        assertThat(created.path("fullName").asText()).isEqualTo("Canonical Engineer");
        assertThat(created.path("nationalId").asText()).isEqualTo("NID-CAN-01");
        assertThat(jdbc.queryForObject(
                "select count(*) from workforce_members where id = ?", Integer.class, memberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from drivers where workforce_member_id = ?", Integer.class, memberId)).isZero();

        Map<String, Object> update = identity("Updated Engineer", "01055550002", "NID-CAN-02");
        update.put("version", created.path("version").asLong());
        JsonNode saved = body(putJson("/api/v1/workforce/members/" + memberId, update, token)
                .andExpect(status().isOk()));

        assertThat(saved.path("fullName").asText()).isEqualTo("Updated Engineer");
        assertThat(saved.path("phone").asText()).isEqualTo("01055550002");
        assertThat(saved.path("version").asLong()).isGreaterThan(created.path("version").asLong());
    }

    @Test
    void canonicalEditAlsoUpdatesLinkedLegacyDriverIdentity() throws Exception {
        String token = registerTenant("Linked Workforce", uniqueName("linkedwf"))
                .path("accessToken").asText();
        Map<String, Object> driver = new HashMap<>();
        driver.put("name", "Before Canonical Edit");
        driver.put("workerType", "DRIVER");
        driver.put("morningRate", 0);
        driver.put("eveningRate", 0);
        driver.put("nightRate", 0);
        JsonNode driverResponse = body(postJson("/api/v1/drivers", driver, token)
                .andExpect(status().isCreated()));
        long driverId = driverResponse.path("id").asLong();
        long memberId = jdbc.queryForObject(
                "select workforce_member_id from drivers where id = ?", Long.class, driverId);

        JsonNode member = body(getJson("/api/v1/workforce/members/" + memberId, token)
                .andExpect(status().isOk()));
        Map<String, Object> update = identity("After Canonical Edit", "01055550003", null);
        update.put("version", member.path("version").asLong());

        putJson("/api/v1/workforce/members/" + memberId, update, token)
                .andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap(
                "select d.name, d.phone, wm.full_name, wm.phone "
                        + "from drivers d join workforce_members wm "
                        + "on wm.id = d.workforce_member_id and wm.tenant_id = d.tenant_id "
                        + "where d.id = ?", driverId);
        assertThat(row.get("name")).isEqualTo("After Canonical Edit");
        assertThat(row.get("phone")).isEqualTo("01055550003");
        assertThat(row.get("full_name")).isEqualTo("After Canonical Edit");
        assertThat(row.get("phone")).isEqualTo("01055550003");
    }

    @Test
    void canonicalMemberCannotBeReadOrChangedAcrossTenants() throws Exception {
        String tokenA = registerTenant("Canonical Tenant A", uniqueName("canonicala"))
                .path("accessToken").asText();
        String tokenB = registerTenant("Canonical Tenant B", uniqueName("canonicalb"))
                .path("accessToken").asText();
        JsonNode member = body(postJson("/api/v1/workforce/members",
                identity("Tenant A Person", null, "NID-PRIVATE"), tokenA)
                .andExpect(status().isCreated()));

        getJson("/api/v1/workforce/members/" + member.path("id").asLong(), tokenB)
                .andExpect(status().isNotFound());
        Map<String, Object> update = identity("Cross Tenant Attempt", null, null);
        update.put("version", member.path("version").asLong());
        putJson("/api/v1/workforce/members/" + member.path("id").asLong(), update, tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    void directoryNeverExposesNationalId() throws Exception {
        String token = registerTenant("Private Workforce", uniqueName("privatewf"))
                .path("accessToken").asText();
        postJson("/api/v1/workforce/members", identity("Private Person", null, "NID-SECRET"), token)
                .andExpect(status().isCreated());

        JsonNode directory = body(getJson("/api/v1/workforce/members", token).andExpect(status().isOk()));
        JsonNode row = directory.path("content").get(0);
        assertThat(row.has("nationalId")).isFalse();
    }

    private Map<String, Object> identity(String name, String phone, String nationalId) {
        Map<String, Object> request = new HashMap<>();
        request.put("fullName", name);
        request.put("phone", phone);
        request.put("nationalId", nationalId);
        request.put("notes", "canonical profile");
        return request;
    }
}
