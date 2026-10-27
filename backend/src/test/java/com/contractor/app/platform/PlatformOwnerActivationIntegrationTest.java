package com.contractor.app.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.platform.domain.PlatformOwner;
import com.contractor.app.platform.domain.PlatformBootstrapService;
import com.contractor.app.platform.security.PlatformTotpService;
import java.time.Instant;
import com.contractor.app.platform.domain.TenantAdminActivationToken;
import com.contractor.app.platform.infra.TenantAdminActivationTokenRepository;
import com.contractor.app.common.security.TokenHasher;
import com.contractor.app.auth.domain.RefreshToken;
import com.contractor.app.auth.infra.RefreshTokenRepository;
import com.contractor.app.tenant.domain.Tenant;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.user.domain.Role;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.contractor.app.platform.infra.PlatformOwnerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.platform.totp-encryption-key=integration-test-only-key-for-platform-mfa-1234567890")
class PlatformOwnerActivationIntegrationTest extends AbstractIntegrationTest {
    @Autowired private PlatformOwnerRepository owners;
    @Autowired private PlatformBootstrapService bootstrap;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PlatformTotpService totp;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private TenantAdminActivationTokenRepository activationTokens;
    @Autowired private RefreshTokenRepository refreshTokens;

    @Test
    void ownerCreatesTenantAdminActivationIsOneTimeAndTokensStaySeparated() throws Exception {
        String bootstrapUsername = uniqueName("bootstrap_");
        bootstrap.createInitialOwner(bootstrapUsername, "Bootstrap Owner", "BootstrapStrongPass123");
        assertThrows(IllegalStateException.class, () -> bootstrap.createInitialOwner(bootstrapUsername, "Second Owner", "BootstrapStrongPass123"));

        String ownerUsername = uniqueName("platform_");
        owners.saveAndFlush(new PlatformOwner(ownerUsername, "Platform Owner", encoder.encode("StrongOwnerPass123")));

        String ownerToken = loginOwnerToken(ownerUsername, "StrongOwnerPass123");

        String adminUsername = uniqueName("admin_");
        Map<String, Object> create = new HashMap<>();
        create.put("tenantName", "Activation tenant");
        create.put("adminFullName", "First Admin");
        create.put("adminUsername", adminUsername);
        create.put("adminPhone", "");
        create.put("adminEmail", "first-admin@example.test");
        MvcResult created = mockMvc.perform(post("/api/v1/platform/tenants")
                .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
        String activationUrl = createdBody.path("activationUrl").asText();
        String activationToken = activationUrl.substring(activationUrl.indexOf("token=") + 6);

        User pendingAdmin = users.findByUsername(adminUsername).orElseThrow();
        assertEquals("first-admin@example.test", pendingAdmin.getEmail());
        assertFalse(pendingAdmin.isEmailVerified(), "Email is not verified before activation is completed");

        // The newly created admin has a random temporary password. A guessed password
        // must fail authentication before activation because the credentials do not match.
        Map<String, Object> adminLogin = new HashMap<>();
        adminLogin.put("username", adminUsername);
        adminLogin.put("password", "StrongAdminPass123");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin))).andExpect(status().isUnauthorized());

        String expiredRawToken = TokenHasher.newOpaqueToken();
        Tenant expiredTenant = tenants.saveAndFlush(new Tenant("Expired activation tenant"));
        User expiredAdmin = new User(expiredTenant.getId(), uniqueName("expired_admin_"), "Expired Admin",
                null, encoder.encode("UnusablePass123"), Role.ADMIN);
        expiredAdmin.setPasswordActivated(false);
        expiredAdmin = users.saveAndFlush(expiredAdmin);

        // Correct credentials for an inactive account must be rejected as forbidden.
        Map<String, Object> inactiveAdminLogin = new HashMap<>();
        inactiveAdminLogin.put("username", expiredAdmin.getUsername());
        inactiveAdminLogin.put("password", "UnusablePass123");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inactiveAdminLogin)))
                .andExpect(status().isForbidden());

        activationTokens.saveAndFlush(new TenantAdminActivationToken(expiredTenant.getId(), expiredAdmin.getId(),
                TokenHasher.sha256Hex(expiredRawToken), LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
        Map<String, Object> expiredActivation = new HashMap<>();
        expiredActivation.put("token", expiredRawToken);
        expiredActivation.put("password", "StrongAdminPass123");
        mockMvc.perform(post("/api/v1/platform/admin-activation").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(expiredActivation))).andExpect(status().isBadRequest());

        Map<String, Object> activate = new HashMap<>();
        activate.put("token", activationToken);
        activate.put("password", "StrongAdminPass123");
        mockMvc.perform(post("/api/v1/platform/admin-activation").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(activate))).andExpect(status().isNoContent());
        User activatedAdmin = users.findByUsername(adminUsername).orElseThrow();
        assertTrue(activatedAdmin.isEmailVerified(), "Successful activation link proves control of the email inbox");
        mockMvc.perform(post("/api/v1/platform/admin-activation").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(activate))).andExpect(status().isBadRequest());

        MvcResult tenantLogin = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin))).andExpect(status().isOk()).andReturn();
        String tenantToken = objectMapper.readTree(tenantLogin.getResponse().getContentAsString()).path("accessToken").asText();
        mockMvc.perform(post("/api/v1/platform/tenants").header("Authorization", "Bearer " + tenantToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledPlatformOwnerCannotUsePreviouslyIssuedAccessToken() throws Exception {
        String ownerUsername = uniqueName("disabled_owner_");
        PlatformOwner owner = owners.saveAndFlush(
                new PlatformOwner(ownerUsername, "Platform Owner", encoder.encode("StrongOwnerPass123")));

        String ownerToken = loginOwnerToken(ownerUsername, "StrongOwnerPass123");

        owner.setActive(false);
        owners.saveAndFlush(owner);

        Map<String, Object> create = new HashMap<>();
        create.put("tenantName", "Must not be created");
        create.put("adminFullName", "First Admin");
        create.put("adminUsername", uniqueName("blocked_admin_"));
        create.put("adminPhone", "");

        mockMvc.perform(post("/api/v1/platform/tenants")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inactiveTenantAdminCannotRefreshSessionBeforeActivation() throws Exception {
        Tenant tenant = tenants.saveAndFlush(new Tenant("Refresh before activation tenant"));
        User admin = new User(tenant.getId(), uniqueName("pending_admin_"), "Pending Admin",
                null, encoder.encode("UnusablePass123"), Role.ADMIN);
        admin.setPasswordActivated(false);
        admin = users.saveAndFlush(admin);

        String rawRefreshToken = TokenHasher.newOpaqueToken();
        refreshTokens.saveAndFlush(new RefreshToken(tenant.getId(), admin.getId(),
                TokenHasher.sha256Hex(rawRefreshToken), LocalDateTime.now(ZoneOffset.UTC).plusDays(1)));

        Map<String, Object> refresh = new HashMap<>();
        refresh.put("refreshToken", rawRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentActivationAttemptsCannotBothConsumeTheSameToken() throws Exception {
        String rawToken = TokenHasher.newOpaqueToken();
        Tenant tenant = tenants.saveAndFlush(new Tenant("Concurrent activation tenant"));
        User admin = new User(tenant.getId(), uniqueName("concurrent_admin_"), "Concurrent Admin",
                null, encoder.encode("UnusablePass123"), Role.ADMIN);
        admin.setPasswordActivated(false);
        admin = users.saveAndFlush(admin);
        activationTokens.saveAndFlush(new TenantAdminActivationToken(tenant.getId(), admin.getId(),
                TokenHasher.sha256Hex(rawToken), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10)));

        Map<String, Object> activate = new HashMap<>();
        activate.put("token", rawToken);
        activate.put("password", "StrongAdminPass123");
        String requestBody = objectMapper.writeValueAsString(activate);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> attempt = () -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to start concurrent activation request");
                }
                return mockMvc.perform(post("/api/v1/platform/admin-activation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                        .andReturn().getResponse().getStatus();
            };
            java.util.concurrent.Future<Integer> first = executor.submit(attempt);
            java.util.concurrent.Future<Integer> second = executor.submit(attempt);
            if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent activation requests did not become ready");
            }
            start.countDown();

            int firstStatus = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            int secondStatus = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, java.util.stream.Stream.of(firstStatus, secondStatus)
                    .filter(code -> code == 204).count(), "Exactly one activation request should succeed");
            assertEquals(1, java.util.stream.Stream.of(firstStatus, secondStatus)
                    .filter(code -> code == 400).count(), "The replayed concurrent request should be rejected");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tenantStatusCanBeSuspendedAndReactivatedOnlyByPlatformOwner() throws Exception {
        Tenant tenant = tenants.saveAndFlush(new Tenant("Status transition tenant"));
        String ownerUsername = uniqueName("status_owner_");
        owners.saveAndFlush(new PlatformOwner(ownerUsername, "Status Owner", encoder.encode("StrongOwnerPass123")));

        String ownerToken = loginOwnerToken(ownerUsername, "StrongOwnerPass123");

        Map<String, Object> suspend = new HashMap<>();
        suspend.put("status", "SUSPENDED");
        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}/status", tenant.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(suspend)))
                .andExpect(status().isNoContent());
        assertEquals(com.contractor.app.tenant.domain.TenantStatus.SUSPENDED,
                tenants.findById(tenant.getId()).orElseThrow().getStatus());

        Map<String, Object> reactivate = new HashMap<>();
        reactivate.put("status", "ACTIVE");
        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}/status", tenant.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(reactivate)))
                .andExpect(status().isNoContent());
        assertEquals(com.contractor.app.tenant.domain.TenantStatus.ACTIVE,
                tenants.findById(tenant.getId()).orElseThrow().getStatus());

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}/status", tenant.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(suspend)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}/status", 99999999L)
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(suspend)))
                .andExpect(status().isNotFound());

        Map<String, Object> invalid = new HashMap<>();
        invalid.put("status", "DELETED");
        mockMvc.perform(patch("/api/v1/platform/tenants/{tenantId}/status", tenant.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    private String loginOwnerToken(String username, String password) throws Exception {
        Map<String, Object> login = new HashMap<>();
        login.put("username", username);
        login.put("password", password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk()).andReturn();
        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String challenge = loginBody.path("challengeToken").asText();
        if ("MFA_SETUP_REQUIRED".equals(loginBody.path("status").asText())) {
            Map<String, Object> setupRequest = new HashMap<>();
            setupRequest.put("challengeToken", challenge);
            MvcResult setupResult = mockMvc.perform(post("/api/v1/platform/auth/mfa/setup")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(setupRequest)))
                    .andExpect(status().isOk()).andReturn();
            String secret = objectMapper.readTree(setupResult.getResponse().getContentAsString()).path("secret").asText();
            Map<String, Object> codeRequest = new HashMap<>();
            codeRequest.put("challengeToken", challenge);
            codeRequest.put("code", totp.codeAt(secret, Instant.now().getEpochSecond() / 30L));
            MvcResult enabled = mockMvc.perform(post("/api/v1/platform/auth/mfa/enable")
                    .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(codeRequest)))
                    .andExpect(status().isOk()).andReturn();
            return objectMapper.readTree(enabled.getResponse().getContentAsString()).path("accessToken").asText();
        }
        throw new IllegalStateException("Unexpected MFA state for test owner");
    }


}
