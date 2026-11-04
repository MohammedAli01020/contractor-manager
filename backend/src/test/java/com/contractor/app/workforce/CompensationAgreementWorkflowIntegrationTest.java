package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

class CompensationAgreementWorkflowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String token;
    private long memberId;
    private long driverAssignmentId;
    private long workerAssignmentId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode owner = registerTenant("Compensation Workflow Co", uniqueName("compwf"));
        token = owner.path("accessToken").asText();
        JsonNode roles = body(getJson("/api/v1/workforce/roles", token).andExpect(status().isOk()));
        long driverRole = roleId(roles, "DRIVER");
        long workerRole = roleId(roles, "WORKER");

        JsonNode driver = body(postJson("/api/v1/drivers", Map.of(
                "name", "Compensation Workflow Person", "workerType", "DRIVER",
                "morningRate", 0, "eveningRate", 0, "nightRate", 0), token)
                .andExpect(status().isCreated()));
        memberId = jdbc.queryForObject(
                "select workforce_member_id from drivers where id = ?",
                Long.class, driver.path("id").asLong());

        driverAssignmentId = createAssignment(driverRole);
        workerAssignmentId = createAssignment(workerRole);
    }

    @Test
    void differentAssignmentAgreementsMayOverlapButMemberLevelAgreementConflictsWithBoth() throws Exception {
        long driverAgreement = createAgreement(driverAssignmentId, "2026-01-01", null, null, null);
        approve(driverAgreement);
        long workerAgreement = createAgreement(workerAssignmentId, "2026-01-01", null, null, null);
        approve(workerAgreement);

        long memberAgreement = createAgreement(null, "2026-01-01", null, null, null);
        postJson(path(memberAgreement, "approve"), Map.of(), token).andExpect(status().isConflict());
    }

    @Test
    void replacementClosesOldAgreementAndApprovesNewAgreementWithAudit() throws Exception {
        long oldId = createAgreement(null, "2026-01-01", null, null, null);
        JsonNode oldApproved = approve(oldId);

        long newId = createAgreement(null, "2026-12-01", null, oldId, "تعديل الأجر باتفاق الطرفين");
        JsonNode replacement = body(postJson(path(newId, "approve-replacement"), Map.of(), token)
                .andExpect(status().isOk()));

        assertThat(replacement.path("status").asText()).isEqualTo("APPROVED");
        assertThat(replacement.path("supersedesAgreementId").asLong()).isEqualTo(oldId);
        assertThat(replacement.path("changeReason").asText()).isEqualTo("تعديل الأجر باتفاق الطرفين");
        assertThat(replacement.path("approvedByUserId").asLong()).isPositive();
        assertThat(replacement.path("approvedAt").asText()).isNotBlank();

        JsonNode all = body(getJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", token).andExpect(status().isOk()));
        JsonNode old = find(all, oldId);
        assertThat(old.path("status").asText()).isEqualTo("SUPERSEDED");
        assertThat(old.path("effectiveTo").asText()).isEqualTo("2026-11-30");
        assertThat(old.path("approvedByUserId").asLong())
                .isEqualTo(oldApproved.path("approvedByUserId").asLong());
    }

    @Test
    void cancelledDraftDoesNotBecomeApprovedAndIsVisibleInHistory() throws Exception {
        long draftId = createAgreement(null, "2026-01-01", null, null, null);
        postJson(path(draftId, "cancel"), Map.of(), token).andExpect(status().isNoContent());
        postJson(path(draftId, "approve"), Map.of(), token).andExpect(status().isConflict());

        JsonNode all = body(getJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", token).andExpect(status().isOk()));
        assertThat(find(all, draftId).path("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void anotherTenantCannotReadOrAttachAgreementToThisMember() throws Exception {
        String otherToken = registerTenant("Other Compensation Co", uniqueName("othercomp"))
                .path("accessToken").asText();
        getJson("/api/v1/workforce/members/" + memberId + "/compensation-agreements", otherToken)
                .andExpect(status().isNotFound());

        Map<String, Object> request = agreementBody(null, "2026-01-01", null, null, null);
        postJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements", request, otherToken).andExpect(status().isNotFound());
    }

    private long createAssignment(long roleId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("roleId", roleId);
        request.put("effectiveFrom", "2026-01-01");
        JsonNode result = body(postJson("/api/v1/workforce/members/" + memberId + "/assignments",
                request, token).andExpect(status().isCreated()));
        return result.path("id").asLong();
    }

    private long createAgreement(Long assignmentId, String from, String to,
                                 Long supersedesId, String reason) throws Exception {
        JsonNode result = body(postJson("/api/v1/workforce/members/" + memberId
                + "/compensation-agreements",
                agreementBody(assignmentId, from, to, supersedesId, reason), token)
                .andExpect(status().isCreated()));
        return result.path("id").asLong();
    }

    private Map<String, Object> agreementBody(Long assignmentId, String from, String to,
                                               Long supersedesId, String reason) {
        Map<String, Object> request = new HashMap<>();
        request.put("workforceAssignmentId", assignmentId);
        request.put("effectiveFrom", from);
        request.put("effectiveTo", to);
        request.put("currencyCode", "EGP");
        request.put("supersedesAgreementId", supersedesId);
        request.put("changeReason", reason);
        request.put("components", List.of(Map.of(
                "componentCode", "MORNING_RATE",
                "displayName", "أجر الوردية الصباحية",
                "basis", "SHIFT",
                "rate", 300,
                "shiftType", "MORNING",
                "sortOrder", 0)));
        return request;
    }

    private JsonNode approve(long agreementId) throws Exception {
        return body(postJson(path(agreementId, "approve"), Map.of(), token)
                .andExpect(status().isOk()));
    }

    private String path(long agreementId, String action) {
        return "/api/v1/workforce/members/" + memberId
                + "/compensation-agreements/" + agreementId + "/" + action;
    }

    private JsonNode find(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.path("id").asLong() == id) return item;
        }
        throw new AssertionError("Missing compensation agreement " + id);
    }

    private long roleId(JsonNode roles, String code) {
        for (JsonNode role : roles) {
            if (code.equals(role.path("code").asText())) return role.path("id").asLong();
        }
        throw new AssertionError("Missing seeded role: " + code);
    }
}
