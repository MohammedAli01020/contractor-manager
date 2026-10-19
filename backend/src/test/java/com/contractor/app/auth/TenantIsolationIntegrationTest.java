package com.contractor.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.common.security.JwtService;
import com.contractor.app.tenant.domain.Tenant;
import com.contractor.app.tenant.domain.TenantStatus;
import com.contractor.app.tenant.infra.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** أهم اختبارات المشروع: مقاول (أ) لا يرى ولا يعدّل ولا يتخطى بيانات مقاول (ب). */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JwtService jwtService;

    private String tokenA;
    private String tokenB;
    private long tenantA;
    private long tenantB;
    private long userA;
    private long userB;
    private long staffAId;
    private String staffAName;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode a = registerTenant("Tenant A", uniqueName("ownera"));
        JsonNode b = registerTenant("Tenant B", uniqueName("ownerb"));
        tokenA = a.path("accessToken").asText();
        tokenB = b.path("accessToken").asText();
        tenantA = a.path("user").path("tenantId").asLong();
        tenantB = b.path("user").path("tenantId").asLong();
        userA = a.path("user").path("id").asLong();
        userB = b.path("user").path("id").asLong();
        assertThat(tenantA).isNotEqualTo(tenantB);

        staffAName = uniqueName("staffa");
        JsonNode staff = body(postJson("/api/v1/users", userBody(staffAName), tokenA)
                .andExpect(status().isCreated()));
        staffAId = staff.path("id").asLong();
    }

    @Test
    void userListsNeverMixTenants() throws Exception {
        Set<Long> idsA = ids(body(getJson("/api/v1/users", tokenA).andExpect(status().isOk())));
        Set<Long> idsB = ids(body(getJson("/api/v1/users", tokenB).andExpect(status().isOk())));

        assertThat(idsA).containsExactlyInAnyOrder(userA, staffAId);
        assertThat(idsB).containsExactly(userB);
    }

    @Test
    void otherTenantsUserLooksNonexistent() throws Exception {
        getJson("/api/v1/users/" + staffAId, tokenB).andExpect(status().isNotFound());

        Map<String, Object> update = new HashMap<>();
        update.put("fullName", "Hijacked");
        update.put("active", false);
        putJson("/api/v1/users/" + staffAId, update, tokenB).andExpect(status().isNotFound());

        Map<String, Object> reset = new HashMap<>();
        reset.put("newPassword", "Hijack1234567");
        putJson("/api/v1/users/" + staffAId + "/password", reset, tokenB).andExpect(status().isNotFound());

        // وبيانات المستخدم عند صاحبه لم تتغير، وكلمة مروره الأصلية ما زالت تعمل
        getJson("/api/v1/users/" + staffAId, tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Staff " + staffAName))
                .andExpect(jsonPath("$.active").value(true));
        Map<String, Object> login = new HashMap<>();
        login.put("username", staffAName);
        login.put("password", PASSWORD);
        postJson("/api/v1/auth/login", login, null).andExpect(status().isOk());
    }

    @Test
    void tenantIdSentInRequestBodyIsIgnored() throws Exception {
        Map<String, Object> body = userBody(uniqueName("injected"));
        body.put("tenantId", tenantA); // محاولة إنشاء مستخدم داخل مقاول آخر

        JsonNode created = body(postJson("/api/v1/users", body, tokenB).andExpect(status().isCreated()));

        assertThat(created.path("tenantId").asLong()).isEqualTo(tenantB);
        Set<Long> idsA = ids(body(getJson("/api/v1/users", tokenA).andExpect(status().isOk())));
        assertThat(idsA).doesNotContain(created.path("id").asLong());
    }

    @Test
    void tokenWithMismatchedTenantIsRejected() throws Exception {
        String forged = jwtService.createAccessToken(userA, tenantB);

        getJson("/api/v1/auth/me", forged).andExpect(status().isUnauthorized());
        getJson("/api/v1/users", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        JwtService attacker = new JwtService("attacker-secret-attacker-secret-123456", "contractor-manager", 15);
        String forged = attacker.createAccessToken(userA, tenantA);

        getJson("/api/v1/auth/me", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedTenantIsLockedOutImmediately() throws Exception {
        Tenant tenant = tenantRepository.findById(tenantA).orElseThrow(IllegalStateException::new);
        tenant.setStatus(TenantStatus.SUSPENDED);
        tenantRepository.saveAndFlush(tenant);
        try {
            getJson("/api/v1/auth/me", tokenA).andExpect(status().isUnauthorized());
            // المقاول الآخر غير متأثر
            getJson("/api/v1/auth/me", tokenB).andExpect(status().isOk());
        } finally {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenantRepository.saveAndFlush(tenant);
        }
        getJson("/api/v1/auth/me", tokenA).andExpect(status().isOk());
    }

    private static Set<Long> ids(JsonNode array) {
        Set<Long> ids = new HashSet<>();
        array.forEach(node -> ids.add(node.path("id").asLong()));
        return ids;
    }
}
