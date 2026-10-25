package com.contractor.app.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.platform.domain.PlatformOwner;
import com.contractor.app.platform.domain.PlatformBootstrapService;
import com.contractor.app.platform.domain.TenantAdminActivationToken;
import com.contractor.app.platform.infra.TenantAdminActivationTokenRepository;
import com.contractor.app.common.security.TokenHasher;
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

class PlatformOwnerActivationIntegrationTest extends AbstractIntegrationTest {
    @Autowired private PlatformOwnerRepository owners;
    @Autowired private PlatformBootstrapService bootstrap;
    @Autowired private PasswordEncoder encoder;
    @Autowired private TenantRepository tenants;
    @Autowired private UserRepository users;
    @Autowired private TenantAdminActivationTokenRepository activationTokens;

    @Test
    void ownerCreatesTenantAdminActivationIsOneTimeAndTokensStaySeparated() throws Exception {
        String bootstrapUsername = uniqueName("bootstrap_");
        bootstrap.createInitialOwner(bootstrapUsername, "Bootstrap Owner", "BootstrapStrongPass123");
        assertThrows(IllegalStateException.class, () -> bootstrap.createInitialOwner(bootstrapUsername, "Second Owner", "BootstrapStrongPass123"));

        String ownerUsername = uniqueName("platform_");
        owners.saveAndFlush(new PlatformOwner(ownerUsername, "Platform Owner", encoder.encode("StrongOwnerPass123")));

        Map<String, Object> login = new HashMap<>();
        login.put("username", ownerUsername);
        login.put("password", "StrongOwnerPass123");
        MvcResult ownerLogin = mockMvc.perform(post("/api/v1/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk()).andReturn();
        String ownerToken = objectMapper.readTree(ownerLogin.getResponse().getContentAsString()).path("accessToken").asText();

        String adminUsername = uniqueName("admin_");
        Map<String, Object> create = new HashMap<>();
        create.put("tenantName", "Activation tenant");
        create.put("adminFullName", "First Admin");
        create.put("adminUsername", adminUsername);
        create.put("adminPhone", "");
        MvcResult created = mockMvc.perform(post("/api/v1/platform/tenants")
                .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated()).andReturn();
        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
        String activationUrl = createdBody.path("activationUrl").asText();
        String activationToken = activationUrl.substring(activationUrl.indexOf("token=") + 6);

        Map<String, Object> adminLogin = new HashMap<>();
        adminLogin.put("username", adminUsername);
        adminLogin.put("password", "StrongAdminPass123");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin))).andExpect(status().isForbidden());

        String expiredRawToken = TokenHasher.newOpaqueToken();
        Tenant expiredTenant = tenants.saveAndFlush(new Tenant("Expired activation tenant"));
        User expiredAdmin = new User(expiredTenant.getId(), uniqueName("expired_admin_"), "Expired Admin",
                null, encoder.encode("UnusablePass123"), Role.ADMIN);
        expiredAdmin.setPasswordActivated(false);
        expiredAdmin = users.saveAndFlush(expiredAdmin);
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
}
