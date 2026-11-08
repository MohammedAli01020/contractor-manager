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
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where entity_type = 'WORKFORCE_MEMBER' "
                        + "and entity_id = ? and (old_value like '%NID-CAN-%' or new_value like '%NID-CAN-%')",
                Integer.class, memberId)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where entity_type = 'WORKFORCE_MEMBER' "
                        + "and entity_id = ? and field_name = 'nationalId' "
                        + "and old_value = '[REDACTED]' and new_value = '[REDACTED]'",
                Integer.class, memberId)).isEqualTo(1);
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

    @Test
    void onlyAdminCanCreateOrUpdateCanonicalMembers() throws Exception {
        String adminToken = registerTenant("Workforce Permissions", uniqueName("wfpermissions"))
                .path("accessToken").asText();
        String username = uniqueName("workforceviewer");
        body(postJson("/api/v1/users", Map.of("fullName", "Workforce Viewer", "username", username,
                "password", PASSWORD, "role", "VIEWER"), adminToken).andExpect(status().isCreated()));
        String viewerToken = body(postJson("/api/v1/auth/login",
                Map.of("username", username, "password", PASSWORD), null).andExpect(status().isOk()))
                .path("accessToken").asText();

        postJson("/api/v1/workforce/members", identity("Viewer Must Not Create", null, null), viewerToken)
                .andExpect(status().isForbidden());

        JsonNode member = body(postJson("/api/v1/workforce/members",
                identity("Admin Created Person", null, null), adminToken).andExpect(status().isCreated()));
        Map<String, Object> update = identity("Viewer Must Not Update", null, null);
        update.put("version", member.path("version").asLong());
        putJson("/api/v1/workforce/members/" + member.path("id").asLong(), update, viewerToken)
                .andExpect(status().isForbidden());
    }


    @Test
    void onboardingCreatesOneCanonicalPersonWithMultipleRolesAndDirectorySummaries() throws Exception {
        String token = registerTenant("Workforce Onboarding", uniqueName("wfonboard"))
                .path("accessToken").asText();
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long engineerRoleId = roleId(roles, "ENGINEER");
        long supervisorRoleId = roleId(roles, "SUPERVISOR");

        Map<String, Object> member = identity("Multi Role Person", "01055550111", null);
        Map<String, Object> engineer = Map.of("roleId", engineerRoleId, "effectiveFrom", "2026-11-07");
        Map<String, Object> supervisor = Map.of("roleId", supervisorRoleId, "effectiveFrom", "2026-11-07");
        JsonNode created = body(postJson("/api/v1/workforce/members/onboard",
                Map.of("member", member, "initialAssignments", java.util.Arrays.asList(engineer, supervisor)), token)
                .andExpect(status().isCreated()));

        long memberId = created.path("member").path("id").asLong();
        assertThat(created.path("assignments").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from workforce_members where id = ?", Integer.class, memberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from workforce_assignments where workforce_member_id = ?",
                Integer.class, memberId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from drivers where workforce_member_id = ?", Integer.class, memberId)).isZero();

        JsonNode directory = body(getJson("/api/v1/workforce/members", token).andExpect(status().isOk()));
        JsonNode row = null;
        for (JsonNode candidate : directory.path("content")) {
            if (candidate.path("workforceMemberId").asLong() == memberId) row = candidate;
        }
        assertThat(row).isNotNull();
        assertThat(row.path("roles").size()).isEqualTo(2);
        assertThat(row.toString()).contains("ENGINEER", "SUPERVISOR");
        assertThat(row.has("nationalId")).isFalse();
        assertThat(row.has("notes")).isFalse();
    }

    @Test
    void onboardingRejectsEmptyRolesAndRollsBackIdentityWhenRoleIsInvalid() throws Exception {
        String token = registerTenant("Workforce Onboarding Validation", uniqueName("wfonboardvalidation"))
                .path("accessToken").asText();
        Integer before = jdbc.queryForObject(
                "select count(*) from workforce_members where full_name in "
                        + "('No Role Person', 'Invalid Role Person')", Integer.class);

        postJson("/api/v1/workforce/members/onboard",
                Map.of("member", identity("No Role Person", null, null), "initialAssignments", java.util.Collections.emptyList()),
                token).andExpect(status().isBadRequest());

        Map<String, Object> invalidAssignment = Map.of("roleId", 999999L, "effectiveFrom", "2026-11-07");
        postJson("/api/v1/workforce/members/onboard",
                Map.of("member", identity("Invalid Role Person", null, null),
                        "initialAssignments", java.util.Collections.singletonList(invalidAssignment)), token)
                .andExpect(status().isNotFound());

        Integer after = jdbc.queryForObject(
                "select count(*) from workforce_members where full_name in "
                        + "('No Role Person', 'Invalid Role Person')", Integer.class);
        assertThat(after).isEqualTo(before);
    }


    @Test
    void operationalRoleCreatesOneLegacyShiftProjectionAndCandidateRequiresEffectiveAssignmentDate()
            throws Exception {
        String token = registerTenant("Workforce Shift Eligibility", uniqueName("wfshifteligibility"))
                .path("accessToken").asText();
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long driverRoleId = roleId(roles, "DRIVER");

        Map<String, Object> assignment = Map.of(
                "roleId", driverRoleId,
                "effectiveFrom", "2026-11-08");
        JsonNode onboarded = body(postJson("/api/v1/workforce/members/onboard",
                Map.of("member", identity("Eligible Operator", "01055550999", null),
                        "initialAssignments", java.util.Collections.singletonList(assignment)), token)
                .andExpect(status().isCreated()));
        long memberId = onboarded.path("member").path("id").asLong();

        assertThat(jdbc.queryForObject(
                "select count(*) from drivers where tenant_id = ? and workforce_member_id = ?",
                Integer.class, jdbc.queryForObject(
                        "select tenant_id from workforce_members where id = ?", Long.class, memberId),
                memberId)).isEqualTo(1);
        Map<String, Object> projection = jdbc.queryForMap(
                "select worker_type, morning_rate, evening_rate, night_rate, active "
                        + "from drivers where workforce_member_id = ?", memberId);
        assertThat(projection.get("worker_type")).isEqualTo("DRIVER");
        assertThat(((Number) projection.get("morning_rate")).doubleValue()).isZero();
        assertThat(((Number) projection.get("evening_rate")).doubleValue()).isZero();
        assertThat(((Number) projection.get("night_rate")).doubleValue()).isZero();
        assertThat(projection.get("active")).isEqualTo(true);

        JsonNode beforeEffectiveDate = body(getJson(
                "/api/v1/live/workforce-candidates?shiftDate=2026-11-07", token)
                .andExpect(status().isOk()));
        assertThat(beforeEffectiveDate.size()).isZero();

        JsonNode effectiveDate = body(getJson(
                "/api/v1/live/workforce-candidates?shiftDate=2026-11-08", token)
                .andExpect(status().isOk()));
        assertThat(effectiveDate.size()).isEqualTo(1);
        assertThat(effectiveDate.get(0).path("id").asLong()).isEqualTo(
                jdbc.queryForObject("select id from drivers where workforce_member_id = ?",
                        Long.class, memberId));
        assertThat(effectiveDate.get(0).path("name").asText()).isEqualTo("Eligible Operator");
        assertThat(effectiveDate.get(0).path("workerType").asText()).isEqualTo("DRIVER");
    }

    @Test
    void addingOperationalRoleReconcilesExistingInactiveProjectionWithoutResettingAvailabilityOrRates()
            throws Exception {
        String token = registerTenant("Workforce Projection Reconciliation", uniqueName("wfprojection"))
                .path("accessToken").asText();
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long workerRoleId = roleId(roles, "WORKER");
        long driverRoleId = roleId(roles, "DRIVER");

        JsonNode member = body(postJson("/api/v1/workforce/members",
                identity("Canonical Shift Person", "01055550888", null), token)
                .andExpect(status().isCreated()));
        long memberId = member.path("id").asLong();

        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                Map.of("roleId", workerRoleId, "effectiveFrom", "2026-11-08"), token)
                .andExpect(status().isCreated());

        long driverId = jdbc.queryForObject(
                "select id from drivers where workforce_member_id = ?", Long.class, memberId);
        jdbc.update("update drivers set active = false, name = 'Stale Projection', "
                        + "availability = 'UNAVAILABLE', unavailable_reason = 'صيانة', "
                        + "morning_rate = 123, evening_rate = 456, night_rate = 789 where id = ?",
                driverId);

        postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                Map.of("roleId", driverRoleId, "effectiveFrom", "2026-11-08"), token)
                .andExpect(status().isCreated());

        Map<String, Object> reconciled = jdbc.queryForMap(
                "select name, worker_type, active, availability, unavailable_reason, "
                        + "morning_rate, evening_rate, night_rate "
                        + "from drivers where id = ?", driverId);
        assertThat(reconciled.get("name")).isEqualTo("Canonical Shift Person");
        assertThat(reconciled.get("worker_type")).isEqualTo("DRIVER");
        assertThat(reconciled.get("active")).isEqualTo(true);
        assertThat(reconciled.get("availability")).isEqualTo("UNAVAILABLE");
        assertThat(reconciled.get("unavailable_reason")).isEqualTo("صيانة");
        assertThat(((Number) reconciled.get("morning_rate")).doubleValue()).isEqualTo(123);
        assertThat(((Number) reconciled.get("evening_rate")).doubleValue()).isEqualTo(456);
        assertThat(((Number) reconciled.get("night_rate")).doubleValue()).isEqualTo(789);
        assertThat(jdbc.queryForObject(
                "select count(*) from drivers where workforce_member_id = ?", Integer.class, memberId))
                .isEqualTo(1);

        JsonNode candidates = body(getJson(
                "/api/v1/live/workforce-candidates?shiftDate=2026-11-08", token)
                .andExpect(status().isOk()));
        int matchingCandidates = 0;
        for (JsonNode candidate : candidates) {
            if (candidate.path("id").asLong() == driverId) matchingCandidates++;
        }
        assertThat(matchingCandidates).isEqualTo(1);
    }

    @Test
    void shiftCandidatesRespectAssignmentSiteAndEndDate() throws Exception {
        String token = registerTenant("Workforce Site Eligibility", uniqueName("wfsiteeligibility"))
                .path("accessToken").asText();
        long driverRoleId = roleId(
                body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk())), "DRIVER");
        long siteA = body(postJson("/api/v1/sites",
                Map.of("name", uniqueName("WF Site A")), token).andExpect(status().isCreated()))
                .path("id").asLong();
        long siteB = body(postJson("/api/v1/sites",
                Map.of("name", uniqueName("WF Site B")), token).andExpect(status().isCreated()))
                .path("id").asLong();

        JsonNode member = body(postJson("/api/v1/workforce/members",
                identity("Site Assigned Driver", "01055550777", null), token)
                .andExpect(status().isCreated()));
        long memberId = member.path("id").asLong();
        Map<String, Object> assignment = new HashMap<>();
        assignment.put("roleId", driverRoleId);
        assignment.put("siteId", siteA);
        assignment.put("effectiveFrom", "2026-11-08");
        assignment.put("effectiveTo", "2026-11-10");
        postJson("/api/v1/workforce/members/" + memberId + "/assignments", assignment, token)
                .andExpect(status().isCreated());

        JsonNode matchingSite = body(getJson(
                "/api/v1/live/workforce-candidates?shiftDate=2026-11-09&siteId=" + siteA, token)
                .andExpect(status().isOk()));
        assertThat(matchingSite.size()).isEqualTo(1);

        JsonNode differentSite = body(getJson(
                "/api/v1/live/workforce-candidates?shiftDate=2026-11-09&siteId=" + siteB, token)
                .andExpect(status().isOk()));
        assertThat(differentSite.size()).isZero();

        JsonNode beforeStart = body(getJson(
                "/api/v1/live/workforce-candidates?shiftDate=2026-11-07&siteId=" + siteA, token)
                .andExpect(status().isOk()));
        assertThat(beforeStart.size()).isZero();

        JsonNode afterEnd = body(getJson(
                "/api/v1/live/workforce-candidates?shiftDate=2026-11-11&siteId=" + siteA, token)
                .andExpect(status().isOk()));
        assertThat(afterEnd.size()).isZero();
    }

    private long roleId(JsonNode roles, String code) {
        for (JsonNode role : roles) {
            if (code.equals(role.path("code").asText())) return role.path("id").asLong();
        }
        throw new AssertionError("Missing seeded role: " + code);
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
