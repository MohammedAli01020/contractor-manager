package com.contractor.app.auth;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies role boundaries at the HTTP/API layer, not just in the Flutter UI. */
class RoleAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private String adminToken;
    private String operatorToken;
    private String viewerToken;
    private long viewerId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode owner = registerTenant("Role Test Co", uniqueName("roleadmin"));
        adminToken = owner.path("accessToken").asText();

        String operatorName = uniqueName("operator");
        body(postJson("/api/v1/users", userBody(operatorName), adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OPERATOR")));
        operatorToken = login(operatorName).path("accessToken").asText();

        String viewerName = uniqueName("viewer");
        Map<String, Object> viewerRequest = userBody(viewerName);
        viewerRequest.put("role", "VIEWER");
        JsonNode viewer = body(postJson("/api/v1/users", viewerRequest, adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("VIEWER")));
        viewerId = viewer.path("id").asLong();
        viewerToken = login(viewerName).path("accessToken").asText();
    }

    @Test
    void viewerCanReadButCannotWriteMasterData() throws Exception {
        getJson("/api/v1/sites", viewerToken).andExpect(status().isOk());
        postJson("/api/v1/sites", Map.of("name", "Viewer Must Not Create"), viewerToken)
                .andExpect(status().isForbidden());
        putJson("/api/v1/users/" + viewerId, Map.of("fullName", "Forbidden", "active", true), viewerToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReadOperationalDataButCannotManageUsersOrMasterData() throws Exception {
        getJson("/api/v1/sites", operatorToken).andExpect(status().isOk());
        getJson("/api/v1/users", operatorToken).andExpect(status().isForbidden());
        postJson("/api/v1/sites", Map.of("name", "Operator Must Not Create"), operatorToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerAndOperatorCannotWriteDriversEquipmentTypesOrShifts() throws Exception {
        Map<String, Object> validDriver = Map.of(
                "name", "Authorization test",
                "workerType", "DRIVER",
                "morningRate", 0,
                "eveningRate", 0,
                "nightRate", 0);
        Map<String, Object> validEquipment = Map.of(
                "code", "AUTH-TEST",
                "equipmentTypeId", 1,
                "operatingCostPerShift", 0);
        Map<String, Object> validEquipmentType = Map.of("name", "Authorization test");
        Map<String, Object> validShift = Map.of(
                "shiftType", "MORNING",
                "siteId", 1,
                "driverId", 1,
                "equipmentId", 1);
        Map<String, Object> validVersion = Map.of("version", 0);
        Map<String, Object> validCancel = Map.of("reason", "Authorization test", "version", 0);

        for (String token : new String[] {viewerToken, operatorToken}) {
            // Use validation-valid bodies so these assertions exercise authorization, not DTO validation.
            postJson("/api/v1/drivers", validDriver, token).andExpect(status().isForbidden());
            putJson("/api/v1/drivers/1", validDriver, token).andExpect(status().isForbidden());
            deleteJson("/api/v1/drivers/1", token).andExpect(status().isForbidden());

            postJson("/api/v1/equipment", validEquipment, token).andExpect(status().isForbidden());
            putJson("/api/v1/equipment/1", validEquipment, token).andExpect(status().isForbidden());
            deleteJson("/api/v1/equipment/1", token).andExpect(status().isForbidden());

            postJson("/api/v1/equipment-types", validEquipmentType, token).andExpect(status().isForbidden());
            putJson("/api/v1/equipment-types/1", validEquipmentType, token).andExpect(status().isForbidden());
            deleteJson("/api/v1/equipment-types/1", token).andExpect(status().isForbidden());

            postJson("/api/v1/shifts", validShift, token).andExpect(status().isForbidden());
            putJson("/api/v1/shifts/1", validShift, token).andExpect(status().isForbidden());
            postJson("/api/v1/shifts/1/finish", validVersion, token).andExpect(status().isForbidden());
            postJson("/api/v1/shifts/1/cancel", validCancel, token).andExpect(status().isForbidden());
            postJson("/api/v1/shifts/1/reinstate", validVersion, token).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanManageMasterDataAndCannotGrantAdminThroughUserCreation() throws Exception {
        postJson("/api/v1/sites", Map.of("name", "Admin Site"), adminToken)
                .andExpect(status().isCreated());

        Map<String, Object> escalation = userBody(uniqueName("escalation"));
        escalation.put("role", "ADMIN");
        postJson("/api/v1/users", escalation, adminToken)
                .andExpect(status().isBadRequest());
    }

    private JsonNode login(String username) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", PASSWORD);
        return body(postJson("/api/v1/auth/login", request, null).andExpect(status().isOk()));
    }
}
