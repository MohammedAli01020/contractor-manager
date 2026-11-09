package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkforceAssignmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private long memberId;
    private long driverRoleId;
    private long workerRoleId;
    private long siteId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode owner = registerTenant("Workforce Co", uniqueName("wfowner"));
        token = owner.path("accessToken").asText();

        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        driverRoleId = roleId(roles, "DRIVER");
        workerRoleId = roleId(roles, "WORKER");

        JsonNode site = body(postJson("/api/v1/sites", Map.of("name", uniqueName("WF Site")), token)
                .andExpect(status().isCreated()));
        siteId = site.path("id").asLong();

        Map<String, Object> driver = new HashMap<>();
        driver.put("name", "Workforce Assignment Person");
        driver.put("workerType", "DRIVER");
        driver.put("morningRate", 0);
        driver.put("eveningRate", 0);
        driver.put("nightRate", 0);
        long driverId = body(postJson("/api/v1/drivers", driver, token)
                .andExpect(status().isCreated())).path("id").asLong();
        memberId = jdbc.queryForObject(
                "select workforce_member_id from drivers where id = ?", Long.class, driverId);
    }

    @Test
    void tenantReceivesSeededCommonRolesAndCanCreateCustomRoles() throws Exception {
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        assertThat(roles.toString()).contains("DRIVER", "EQUIPMENT_OPERATOR", "WORKER",
                "TECHNICIAN", "SUPERVISOR", "ENGINEER", "ADMINISTRATIVE");

        Map<String, Object> custom = Map.of("code", "QUALITY_INSPECTOR", "name", "مراقب جودة");
        JsonNode created = body(postJson("/api/v1/workforce/roles", custom, token)
                .andExpect(status().isCreated()));
        assertThat(created.path("systemDefined").asBoolean()).isFalse();
        assertThat(created.path("code").asText()).isEqualTo("QUALITY_INSPECTOR");

        postJson("/api/v1/workforce/roles",
                Map.of("code", "QUALITY_INSPECTOR", "name", "مراقب آخر"), token)
                .andExpect(status().isConflict());

        deleteJson("/api/v1/workforce/roles/" + driverRoleId, token)
                .andExpect(status().isConflict());
        deleteJson("/api/v1/workforce/roles/" + created.path("id").asLong(), token)
                .andExpect(status().isNoContent());
        getJson("/api/v1/workforce/roles", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'QUALITY_INSPECTOR')]").doesNotExist());
        JsonNode allRoles = body(getJson("/api/v1/workforce/roles?includeInactive=true", token)
                .andExpect(status().isOk()));
        boolean foundInactive = false;
        for (JsonNode role : allRoles) {
            if ("QUALITY_INSPECTOR".equals(role.path("code").asText())) {
                foundInactive = !role.path("active").asBoolean();
            }
        }
        assertThat(foundInactive).isTrue();
    }


    @Test
    void customRoleCanBeEnabledForShiftsAndThenAppearsInCandidateList() throws Exception {
        JsonNode custom = body(postJson("/api/v1/workforce/roles",
                Map.of("code", "QUALITY_INSPECTOR", "name", "مراقب جودة",
                        "shiftEligible", false), token).andExpect(status().isCreated()));
        assertThat(custom.path("shiftEligible").asBoolean()).isFalse();

        LocalDate today = LocalDate.now();
        body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(custom.path("id").asLong(), today, null), token)
                .andExpect(status().isCreated()));

        String candidateUrl = "/api/v1/live/workforce-candidates?shiftDate=" + today
                + "&siteId=" + siteId;
        JsonNode before = body(getJson(candidateUrl, token).andExpect(status().isOk()));
        assertThat(before.toString()).doesNotContain("QUALITY_INSPECTOR");

        JsonNode enabled = body(putJson("/api/v1/workforce/roles/" + custom.path("id").asLong()
                + "/shift-eligibility",
                Map.of("shiftEligible", true, "version", custom.path("version").asLong()), token)
                .andExpect(status().isOk()));
        assertThat(enabled.path("shiftEligible").asBoolean()).isTrue();

        JsonNode after = body(getJson(candidateUrl, token).andExpect(status().isOk()));
        boolean found = false;
        for (JsonNode candidate : after) {
            if (candidate.path("workforceMemberId").asLong() == memberId) {
                found = true;
                assertThat(candidate.path("id").asLong()).isGreaterThan(0);
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void onePersonCanHoldDifferentRolesButCannotHaveOverlappingSameRole() throws Exception {
        Map<String, Object> driverAssignment = assignment(driverRoleId, LocalDate.of(2026, 1, 1), null);
        JsonNode first = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                driverAssignment, token).andExpect(status().isCreated()));
        assertThat(first.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(first.path("siteId").asLong()).isEqualTo(siteId);

        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(driverRoleId, LocalDate.of(2026, 2, 1), null), token)
                .andExpect(status().isConflict());

        JsonNode secondRole = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(workerRoleId, LocalDate.of(2026, 1, 1), null), token)
                .andExpect(status().isCreated()));
        assertThat(secondRole.path("roleId").asLong()).isEqualTo(workerRoleId);

        JsonNode list = body(getJson("/api/v1/workforce/members/" + memberId + "/assignments", token)
                .andExpect(status().isOk()));
        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void endingAssignmentAllowsANewNonOverlappingAssignmentForSameRole() throws Exception {
        JsonNode first = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(driverRoleId, LocalDate.of(2026, 1, 1), null), token)
                .andExpect(status().isCreated()));

        Map<String, Object> end = Map.of("effectiveTo", "2026-03-31", "version", first.path("version").asLong());
        JsonNode ended = body(putJson("/api/v1/workforce/members/" + memberId + "/assignments/"
                + first.path("id").asLong() + "/end", end, token).andExpect(status().isOk()));
        assertThat(ended.path("status").asText()).isEqualTo("ENDED");
        putJson("/api/v1/workforce/members/" + memberId + "/assignments/"
                + first.path("id").asLong() + "/end", Map.of("effectiveTo", "2026-03-30",
                "version", first.path("version").asLong()), token).andExpect(status().isConflict());
        assertThat(ended.path("effectiveTo").asText()).isEqualTo("2026-03-31");

        JsonNode replacement = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(driverRoleId, LocalDate.of(2026, 4, 1), null), token)
                .andExpect(status().isCreated()));
        assertThat(replacement.path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void invalidDateRangeAndInactiveRoleAreRejected() throws Exception {
        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(driverRoleId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 30)), token)
                .andExpect(status().isBadRequest());

        JsonNode custom = body(postJson("/api/v1/workforce/roles",
                Map.of("code", "TEMP_ROLE", "name", "دور مؤقت"), token).andExpect(status().isCreated()));
        long customRole = custom.path("id").asLong();
        deleteJson("/api/v1/workforce/roles/" + customRole, token).andExpect(status().isNoContent());

        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(customRole, LocalDate.of(2026, 1, 1), null), token)
                .andExpect(status().isConflict());
    }

    @Test
    void crossTenantMemberRoleAndSiteReferencesAreRejected() throws Exception {
        String otherToken = registerTenant("Other Workforce Co", uniqueName("otherwf")).path("accessToken").asText();
        JsonNode otherDriver = body(postJson("/api/v1/drivers", Map.of(
                "name", "Other Tenant Person", "workerType", "DRIVER",
                "morningRate", 0, "eveningRate", 0, "nightRate", 0), otherToken)
                .andExpect(status().isCreated()));
        long otherMemberId = jdbc.queryForObject(
                "select workforce_member_id from drivers where id = ?",
                Long.class, otherDriver.path("id").asLong());

        JsonNode otherRoles = body(getJson("/api/v1/workforce/roles", otherToken));
        long otherDriverRole = roleId(otherRoles, "DRIVER");
        JsonNode otherSite = body(postJson("/api/v1/sites", Map.of("name", uniqueName("Other Site")), otherToken)
                .andExpect(status().isCreated()));

        postJson("/api/v1/workforce/members/" + otherMemberId + "/assignments",
                assignment(driverRoleId, LocalDate.of(2026, 1, 1), null), token)
                .andExpect(status().isNotFound());

        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(otherDriverRole, LocalDate.of(2026, 1, 1), null), token)
                .andExpect(status().isNotFound());

        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignmentWithSite(driverRoleId, otherSite.path("id").asLong()), token)
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminCanChangeRoleCatalogueAndAssignments() throws Exception {
        String staff = uniqueName("viewer");
        body(postJson("/api/v1/users", Map.of("fullName", "Read Only", "username", staff,
                "password", PASSWORD, "role", "VIEWER"), token).andExpect(status().isCreated()));
        String viewerToken = body(postJson("/api/v1/auth/login",
                Map.of("username", staff, "password", PASSWORD), null).andExpect(status().isOk()))
                .path("accessToken").asText();

        postJson("/api/v1/workforce/roles", Map.of("code", "FORBIDDEN_ROLE", "name", "Forbidden"),
                viewerToken).andExpect(status().isForbidden());
        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                assignment(driverRoleId, LocalDate.of(2026, 1, 1), null), viewerToken)
                .andExpect(status().isForbidden());
        getJson("/api/v1/workforce/roles", viewerToken).andExpect(status().isOk());
        getJson("/api/v1/workforce/members/" + memberId + "/assignments", viewerToken)
                .andExpect(status().isOk());
    }

    private Map<String, Object> assignment(long roleId, LocalDate from, LocalDate to) {
        Map<String, Object> body = new HashMap<>();
        body.put("roleId", roleId);
        body.put("siteId", siteId);
        body.put("effectiveFrom", from.toString());
        if (to != null) body.put("effectiveTo", to.toString());
        return body;
    }

    private Map<String, Object> assignmentWithSite(long roleId, long foreignSiteId) {
        Map<String, Object> body = assignment(roleId, LocalDate.of(2026, 1, 1), null);
        body.put("siteId", foreignSiteId);
        return body;
    }

    private long roleId(JsonNode roles, String code) {
        for (JsonNode role : roles) {
            if (code.equals(role.path("code").asText())) return role.path("id").asLong();
        }
        throw new AssertionError("Missing seeded role: " + code);
    }
}
