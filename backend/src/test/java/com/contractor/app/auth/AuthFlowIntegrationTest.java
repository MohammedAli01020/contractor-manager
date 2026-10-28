package com.contractor.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerTenantReturnsTokensAndAdminUser() throws Exception {
        String username = uniqueName("owner");

        JsonNode res = registerTenant("Al Nour Contracting", username);

        assertThat(res.path("accessToken").asText()).isNotBlank();
        assertThat(res.path("refreshToken").asText()).isNotBlank();
        assertThat(res.path("user").path("role").asText()).isEqualTo("ADMIN");
        assertThat(res.path("user").path("username").asText()).isEqualTo(username);
        assertThat(res.path("user").path("tenantId").asLong()).isPositive();
    }

    @Test
    void usernameIsCaseInsensitiveAndUnique() throws Exception {
        String username = uniqueName("owner");
        registerTenant("Company One", username);

        postJson("/api/v1/auth/register-tenant", registerBody("Company Two", username.toUpperCase()), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void loginSucceedsWithRightPasswordAndFailsOtherwise() throws Exception {
        String username = uniqueName("owner");
        registerTenant("Login Co", username);

        Map<String, Object> good = new HashMap<>();
        good.put("username", username);
        good.put("password", PASSWORD);
        postJson("/api/v1/auth/login", good, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        Map<String, Object> wrongPassword = new HashMap<>(good);
        wrongPassword.put("password", "WrongPass123");
        postJson("/api/v1/auth/login", wrongPassword, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        Map<String, Object> unknownUser = new HashMap<>(good);
        unknownUser.put("username", uniqueName("ghost"));
        postJson("/api/v1/auth/login", unknownUser, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void requestIdIsReturnedInResponseHeaderAndApiErrorBody() throws Exception {
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("username", uniqueName("missing"));
        credentials.put("password", "WrongPass123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-ID", "test-request-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(credentials)))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Request-ID", "test-request-42"))
                .andExpect(jsonPath("$.requestId").value("test-request-42"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        String username = uniqueName("owner");
        JsonNode res = registerTenant("Protected Co", username);

        getJson("/api/v1/auth/me", null).andExpect(status().isUnauthorized());
        getJson("/api/v1/auth/me", "garbage.token.value").andExpect(status().isUnauthorized());
        getJson("/api/v1/users", null).andExpect(status().isUnauthorized());

        getJson("/api/v1/auth/me", res.path("accessToken").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void refreshRotatesTokensAndDetectsReuse() throws Exception {
        JsonNode first = registerTenant("Refresh Co", uniqueName("owner"));
        String oldRefresh = first.path("refreshToken").asText();

        Map<String, Object> req = new HashMap<>();
        req.put("refreshToken", oldRefresh);
        JsonNode second = body(postJson("/api/v1/auth/refresh", req, null).andExpect(status().isOk()));
        String newRefresh = second.path("refreshToken").asText();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);

        // إعادة استخدام الرمز القديم مرفوضة...
        postJson("/api/v1/auth/refresh", req, null).andExpect(status().isUnauthorized());

        // ...وتُلغي كل جلسات المستخدم، فحتى الرمز الجديد لم يعد صالحًا
        Map<String, Object> reqNew = new HashMap<>();
        reqNew.put("refreshToken", newRefresh);
        postJson("/api/v1/auth/refresh", reqNew, null).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        JsonNode res = registerTenant("Logout Co", uniqueName("owner"));
        Map<String, Object> req = new HashMap<>();
        req.put("refreshToken", res.path("refreshToken").asText());

        postJson("/api/v1/auth/logout", req, null).andExpect(status().isNoContent());
        postJson("/api/v1/auth/refresh", req, null).andExpect(status().isUnauthorized());
    }

    @Test
    void weakPasswordIsRejectedWithFieldErrors() throws Exception {
        Map<String, Object> body = registerBody("Weak Co", uniqueName("owner"));
        body.put("password", "short");

        postJson("/api/v1/auth/register-tenant", body, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void changingPasswordEndsOtherSessions() throws Exception {
        String username = uniqueName("owner");
        JsonNode res = registerTenant("Change Pw Co", username);
        String access = res.path("accessToken").asText();

        Map<String, Object> wrongCurrent = new HashMap<>();
        wrongCurrent.put("currentPassword", "NotMyPassword1");
        wrongCurrent.put("newPassword", "NewPassw0rd99");
        postJson("/api/v1/auth/change-password", wrongCurrent, access).andExpect(status().isBadRequest());

        Map<String, Object> change = new HashMap<>();
        change.put("currentPassword", PASSWORD);
        change.put("newPassword", "NewPassw0rd99");
        postJson("/api/v1/auth/change-password", change, access).andExpect(status().isNoContent());

        Map<String, Object> oldLogin = new HashMap<>();
        oldLogin.put("username", username);
        oldLogin.put("password", PASSWORD);
        postJson("/api/v1/auth/login", oldLogin, null).andExpect(status().isUnauthorized());

        Map<String, Object> newLogin = new HashMap<>();
        newLogin.put("username", username);
        newLogin.put("password", "NewPassw0rd99");
        postJson("/api/v1/auth/login", newLogin, null).andExpect(status().isOk());

        Map<String, Object> oldRefresh = new HashMap<>();
        oldRefresh.put("refreshToken", res.path("refreshToken").asText());
        postJson("/api/v1/auth/refresh", oldRefresh, null).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        JsonNode owner = registerTenant("Deactivate Co", uniqueName("owner"));
        String ownerToken = owner.path("accessToken").asText();

        String staffName = uniqueName("staff");
        JsonNode staff = body(postJson("/api/v1/users", userBody(staffName), ownerToken)
                .andExpect(status().isCreated()));
        long staffId = staff.path("id").asLong();

        Map<String, Object> login = new HashMap<>();
        login.put("username", staffName);
        login.put("password", PASSWORD);
        String staffToken = body(postJson("/api/v1/auth/login", login, null).andExpect(status().isOk()))
                .path("accessToken").asText();
        getJson("/api/v1/auth/me", staffToken).andExpect(status().isOk());

        Map<String, Object> update = new HashMap<>();
        update.put("fullName", "Staff Disabled");
        update.put("active", false);
        putJson("/api/v1/users/" + staffId, update, ownerToken).andExpect(status().isOk());

        // الرمز الذي كان صالحًا يتوقف فورًا، والدخول الجديد يُرفض
        getJson("/api/v1/auth/me", staffToken).andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/login", login, null).andExpect(status().isForbidden());
    }

    @Test
    void adminCannotDeactivateOwnAccount() throws Exception {
        JsonNode owner = registerTenant("Self Co", uniqueName("owner"));
        long ownerId = owner.path("user").path("id").asLong();

        Map<String, Object> update = new HashMap<>();
        update.put("fullName", "Me");
        update.put("active", false);
        putJson("/api/v1/users/" + ownerId, update, owner.path("accessToken").asText())
                .andExpect(status().isBadRequest());
    }
}
