package com.contractor.app.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Cairo");

    private String token;
    private long userId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode owner = registerTenant("Audit Co", uniqueName("owner"));
        token = owner.path("accessToken").asText();
        userId = owner.path("user").path("id").asLong();
    }

    private JsonNode search(String query, String accessToken) throws Exception {
        return body(getJson("/api/v1/audit" + query, accessToken).andExpect(status().isOk()));
    }

    private long createDriver(String name) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("workerType", "DRIVER");
        body.put("morningRate", new BigDecimal("150"));
        body.put("eveningRate", new BigDecimal("175"));
        body.put("nightRate", new BigDecimal("200"));
        return body(postJson("/api/v1/drivers", body, token).andExpect(status().isCreated())).path("id").asLong();
    }

    private static JsonNode row(JsonNode page, String action, String field) {
        for (JsonNode item : page.path("items")) {
            boolean sameField = field == null || field.equals(item.path("fieldName").asText());
            if (action.equals(item.path("action").asText()) && sameField) {
                return item;
            }
        }
        return null;
    }

    @Test
    void driverRateChangesAreRecordedWithOldAndNewValues() throws Exception {
        long driverId = createDriver("Audited Driver");
        JsonNode driver = body(getJson("/api/v1/drivers/" + driverId, token));

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Audited Driver");
        update.put("workerType", "DRIVER");
        update.put("morningRate", new BigDecimal("999"));
        update.put("eveningRate", new BigDecimal("175"));
        update.put("nightRate", new BigDecimal("200"));
        update.put("version", driver.path("version").asLong());
        putJson("/api/v1/drivers/" + driverId, update, token).andExpect(status().isOk());

        JsonNode page = search("?entityType=DRIVER&entityId=" + driverId, token);
        JsonNode change = row(page, "UPDATED", "morningRate");
        assertThat(change).isNotNull();
        assertThat(change.path("oldValue").asText()).isEqualTo("150.00");
        assertThat(change.path("newValue").asText()).isEqualTo("999.00");
        assertThat(change.path("userId").asLong()).isEqualTo(userId);
        assertThat(change.path("userName").asText()).isNotBlank();
        assertThat(row(page, "CREATED", null)).isNotNull();
        // الأجور التي لم تتغير لا تظهر
        assertThat(row(page, "UPDATED", "eveningRate")).isNull();
    }

    @Test
    void equipmentAndDeactivationAreRecorded() throws Exception {
        Map<String, Object> type = new HashMap<>();
        type.put("name", "Loader");
        long typeId = body(postJson("/api/v1/equipment-types", type, token).andExpect(status().isCreated()))
                .path("id").asLong();
        Map<String, Object> eq = new HashMap<>();
        eq.put("code", "LD-01");
        eq.put("equipmentTypeId", typeId);
        eq.put("operatingCostPerShift", new BigDecimal("500"));
        eq.put("defaultShiftPrice", new BigDecimal("1200"));
        JsonNode created = body(postJson("/api/v1/equipment", eq, token).andExpect(status().isCreated()));
        long id = created.path("id").asLong();

        eq.put("defaultShiftPrice", new BigDecimal("1500"));
        eq.put("status", "MAINTENANCE");
        eq.put("version", created.path("version").asLong());
        putJson("/api/v1/equipment/" + id, eq, token).andExpect(status().isOk());
        deleteJson("/api/v1/equipment/" + id, token).andExpect(status().isNoContent());

        JsonNode page = search("?entityType=EQUIPMENT&entityId=" + id, token);
        assertThat(row(page, "UPDATED", "defaultShiftPrice").path("newValue").asText()).isEqualTo("1500.00");
        assertThat(row(page, "UPDATED", "status").path("newValue").asText()).isEqualTo("MAINTENANCE");
        assertThat(row(page, "DEACTIVATED", "active")).isNotNull();
    }

    @Test
    void userEventsAreRecordedWithoutLeakingPasswords() throws Exception {
        Map<String, Object> newUser = userBody(uniqueName("staff"));
        JsonNode created = body(postJson("/api/v1/users", newUser, token).andExpect(status().isCreated()));
        long id = created.path("id").asLong();

        Map<String, Object> reset = new HashMap<>();
        reset.put("newPassword", "BrandNewPass123");
        putJson("/api/v1/users/" + id + "/password", reset, token).andExpect(status().isNoContent());

        Map<String, Object> update = new HashMap<>();
        update.put("fullName", "Renamed Staff");
        update.put("active", false);
        putJson("/api/v1/users/" + id, update, token).andExpect(status().isOk());

        JsonNode page = search("?entityType=USER&entityId=" + id, token);
        assertThat(row(page, "CREATED", null)).isNotNull();
        assertThat(row(page, "PASSWORD_RESET", null)).isNotNull();
        assertThat(row(page, "UPDATED", "active").path("newValue").asText()).isEqualTo("false");
        assertThat(row(page, "UPDATED", "fullName").path("newValue").asText()).isEqualTo("Renamed Staff");
        // لا يوجد في السجل أي أثر لكلمة المرور
        assertThat(page.toString()).doesNotContain("BrandNewPass123").doesNotContain(PASSWORD);
    }

    @Test
    void passwordChangeIsRecorded() throws Exception {
        Map<String, Object> change = new HashMap<>();
        change.put("currentPassword", PASSWORD);
        change.put("newPassword", "AnotherPass999");
        postJson("/api/v1/auth/change-password", change, token).andExpect(status().isNoContent());

        // الجلسات أُنهيت لكن رمز الدخول القصير ما زال صالحًا حتى ينتهي
        JsonNode page = search("?entityType=USER&entityId=" + userId, token);
        assertThat(row(page, "PASSWORD_CHANGED", null)).isNotNull();
    }

    @Test
    void searchFiltersByUserDatesAndPages() throws Exception {
        long first = createDriver("Driver A");
        createDriver("Driver B");
        createDriver("Driver C");

        assertThat(search("?userId=" + userId, token).path("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(search("?userId=" + (userId + 100000), token).path("totalElements").asLong()).isZero();

        LocalDate today = LocalDate.now(ZONE);
        assertThat(search("?from=" + today.minusDays(1) + "&to=" + today.plusDays(1), token)
                .path("totalElements").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(search("?from=2000-01-01&to=2000-01-02", token).path("totalElements").asLong()).isZero();

        JsonNode paged = search("?entityType=DRIVER&size=2", token);
        assertThat(paged.path("items").size()).isEqualTo(2);
        assertThat(paged.path("totalPages").asInt()).isEqualTo(2);
        assertThat(search("?entityType=DRIVER&entityId=" + first, token).path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void auditLogIsIsolatedBetweenTenants() throws Exception {
        long driverId = createDriver("Tenant A Driver");
        String otherToken = registerTenant("Other Audit Co", uniqueName("owner")).path("accessToken").asText();

        assertThat(search("", otherToken).path("totalElements").asLong()).isZero();
        assertThat(search("?entityType=DRIVER&entityId=" + driverId, otherToken).path("totalElements").asLong())
                .isZero();
        assertThat(search("?entityType=DRIVER&entityId=" + driverId, token).path("totalElements").asLong())
                .isEqualTo(1);
        getJson("/api/v1/audit", null).andExpect(status().isUnauthorized());
    }
}
