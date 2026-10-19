package com.contractor.app.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MasterDataIntegrationTest extends AbstractIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = registerTenant("Master Data Co", uniqueName("owner")).path("accessToken").asText();
    }

    // ---------- sites ----------

    @Test
    void siteLifecycleWithVersionCheckAndSoftDelete() throws Exception {
        String name = "Site " + uniqueName("s");
        JsonNode created = body(postJson("/api/v1/sites", Map.of("name", name, "address", "Cairo"), token)
                .andExpect(status().isCreated()));
        long id = created.path("id").asLong();
        long version = created.path("version").asLong();

        // الاسم مكرر (بغض النظر عن حالة الأحرف) -> 409
        postJson("/api/v1/sites", Map.of("name", name.toUpperCase()), token).andExpect(status().isConflict());

        Map<String, Object> update = new HashMap<>();
        update.put("name", name + " v2");
        update.put("version", version);
        JsonNode updated = body(putJson("/api/v1/sites/" + id, update, token).andExpect(status().isOk()));
        assertThat(updated.path("name").asText()).isEqualTo(name + " v2");
        assertThat(updated.path("version").asLong()).isGreaterThan(version);

        // إرسال النسخة القديمة (تعديل متزامن) -> 409
        putJson("/api/v1/sites/" + id, update, token).andExpect(status().isConflict());

        // بدون version -> 400
        Map<String, Object> noVersion = new HashMap<>();
        noVersion.put("name", name + " v3");
        putJson("/api/v1/sites/" + id, noVersion, token).andExpect(status().isBadRequest());

        // الحذف تعطيل: يختفي من القائمة العادية ويظهر مع includeInactive
        deleteJson("/api/v1/sites/" + id, token).andExpect(status().isNoContent());
        assertThat(containsId(body(getJson("/api/v1/sites", token)), id)).isFalse();
        JsonNode withInactive = body(getJson("/api/v1/sites?includeInactive=true", token));
        assertThat(containsId(withInactive, id)).isTrue();
        getJson("/api/v1/sites/" + id, token).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void siteListIsPagedAndSearchable() throws Exception {
        for (int i = 1; i <= 3; i++) {
            postJson("/api/v1/sites", Map.of("name", "Alpha-" + i), token).andExpect(status().isCreated());
        }
        postJson("/api/v1/sites", Map.of("name", "Beta-1"), token).andExpect(status().isCreated());

        JsonNode page0 = body(getJson("/api/v1/sites?size=3&page=0", token).andExpect(status().isOk()));
        assertThat(page0.path("totalElements").asLong()).isEqualTo(4);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.path("items").size()).isEqualTo(3);

        JsonNode page1 = body(getJson("/api/v1/sites?size=3&page=1", token));
        assertThat(page1.path("items").size()).isEqualTo(1);

        JsonNode search = body(getJson("/api/v1/sites?q=alpha", token));
        assertThat(search.path("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void blankSiteNameIsRejected() throws Exception {
        postJson("/api/v1/sites", Map.of("name", "  "), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    // ---------- drivers ----------

    private Map<String, Object> driverBody(String name, String type) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("phone", "0100000" + (int) (Math.random() * 9000 + 1000));
        map.put("workerType", type);
        map.put("morningRate", new BigDecimal("150.50"));
        map.put("eveningRate", new BigDecimal("175"));
        map.put("nightRate", new BigDecimal("200.25"));
        return map;
    }

    @Test
    void driverStoresRatesAsExactMoney() throws Exception {
        JsonNode created = body(postJson("/api/v1/drivers", driverBody("Ahmed Salem", "DRIVER"), token)
                .andExpect(status().isCreated()));

        assertThat(created.path("morningRate").decimalValue()).isEqualByComparingTo("150.50");
        assertThat(created.path("eveningRate").decimalValue()).isEqualByComparingTo("175.00");
        assertThat(created.path("nightRate").decimalValue()).isEqualByComparingTo("200.25");
        assertThat(created.path("workerType").asText()).isEqualTo("DRIVER");
    }

    @Test
    void driverRatesAreValidated() throws Exception {
        Map<String, Object> negative = driverBody("Neg", "DRIVER");
        negative.put("morningRate", new BigDecimal("-1"));
        postJson("/api/v1/drivers", negative, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> threeDecimals = driverBody("Dec", "DRIVER");
        threeDecimals.put("nightRate", new BigDecimal("10.123"));
        postJson("/api/v1/drivers", threeDecimals, token).andExpect(status().isBadRequest());

        Map<String, Object> missing = driverBody("Missing", "DRIVER");
        missing.remove("eveningRate");
        postJson("/api/v1/drivers", missing, token).andExpect(status().isBadRequest());

        Map<String, Object> badType = driverBody("BadType", "PILOT");
        postJson("/api/v1/drivers", badType, token).andExpect(status().isBadRequest());
    }

    @Test
    void driversCanBeSearchedAndFilteredByType() throws Exception {
        postJson("/api/v1/drivers", driverBody("Ahmed Salem", "DRIVER"), token).andExpect(status().isCreated());
        postJson("/api/v1/drivers", driverBody("Mona Ali", "DRIVER"), token).andExpect(status().isCreated());
        postJson("/api/v1/drivers", driverBody("Karim Worker", "WORKER"), token).andExpect(status().isCreated());

        assertThat(body(getJson("/api/v1/drivers?q=ahm", token)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(body(getJson("/api/v1/drivers?type=WORKER", token)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(body(getJson("/api/v1/drivers", token)).path("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void driverUpdateRequiresCurrentVersion() throws Exception {
        JsonNode created = body(postJson("/api/v1/drivers", driverBody("Old Name", "DRIVER"), token)
                .andExpect(status().isCreated()));
        long id = created.path("id").asLong();

        Map<String, Object> update = driverBody("New Name", "DRIVER");
        update.put("version", created.path("version").asLong());
        update.put("morningRate", new BigDecimal("300"));
        JsonNode updated = body(putJson("/api/v1/drivers/" + id, update, token).andExpect(status().isOk()));
        assertThat(updated.path("morningRate").decimalValue()).isEqualByComparingTo("300.00");

        putJson("/api/v1/drivers/" + id, update, token).andExpect(status().isConflict());
    }

    // ---------- equipment ----------

    private long createType(String name) throws Exception {
        return body(postJson("/api/v1/equipment-types", Map.of("name", name), token)
                .andExpect(status().isCreated())).path("id").asLong();
    }

    private Map<String, Object> equipmentBody(String code, long typeId) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("name", "Machine " + code);
        map.put("equipmentTypeId", typeId);
        map.put("operatingCostPerShift", new BigDecimal("500"));
        return map;
    }

    @Test
    void equipmentCodeIsUniquePerTenantOnly() throws Exception {
        long typeId = createType("Excavator");
        JsonNode created = body(postJson("/api/v1/equipment", equipmentBody("EX-01", typeId), token)
                .andExpect(status().isCreated()));
        assertThat(created.path("equipmentTypeName").asText()).isEqualTo("Excavator");
        assertThat(created.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(created.path("operatingCostPerShift").decimalValue()).isEqualByComparingTo("500.00");

        postJson("/api/v1/equipment", equipmentBody("ex-01", typeId), token).andExpect(status().isConflict());

        // نفس الكود عند مقاول آخر مسموح
        String otherToken = registerTenant("Other Co", uniqueName("owner")).path("accessToken").asText();
        long otherType = body(postJson("/api/v1/equipment-types", Map.of("name", "Excavator"), otherToken)
                .andExpect(status().isCreated())).path("id").asLong();
        postJson("/api/v1/equipment", equipmentBody("EX-01", otherType), otherToken)
                .andExpect(status().isCreated());
    }

    @Test
    void equipmentFiltersAndStatusUpdate() throws Exception {
        long excavator = createType("Excavator");
        long loader = createType("Loader");
        JsonNode first = body(postJson("/api/v1/equipment", equipmentBody("EX-01", excavator), token)
                .andExpect(status().isCreated()));
        postJson("/api/v1/equipment", equipmentBody("LD-01", loader), token).andExpect(status().isCreated());

        assertThat(body(getJson("/api/v1/equipment?typeId=" + loader, token)).path("totalElements").asLong())
                .isEqualTo(1);
        assertThat(body(getJson("/api/v1/equipment?q=ex-0", token)).path("totalElements").asLong()).isEqualTo(1);

        Map<String, Object> update = equipmentBody("EX-01", excavator);
        update.put("version", first.path("version").asLong());
        update.put("status", "MAINTENANCE");
        putJson("/api/v1/equipment/" + first.path("id").asLong(), update, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        assertThat(body(getJson("/api/v1/equipment?status=MAINTENANCE", token)).path("totalElements").asLong())
                .isEqualTo(1);
    }

    @Test
    void equipmentTypeInUseCannotBeDeactivated() throws Exception {
        long typeId = createType("Crane");
        JsonNode item = body(postJson("/api/v1/equipment", equipmentBody("CR-01", typeId), token)
                .andExpect(status().isCreated()));

        deleteJson("/api/v1/equipment-types/" + typeId, token).andExpect(status().isConflict());

        deleteJson("/api/v1/equipment/" + item.path("id").asLong(), token).andExpect(status().isNoContent());
        deleteJson("/api/v1/equipment-types/" + typeId, token).andExpect(status().isNoContent());

        // نوع معطّل لا يُقبل لمعدة جديدة
        postJson("/api/v1/equipment", equipmentBody("CR-02", typeId), token).andExpect(status().isBadRequest());
    }

    @Test
    void equipmentTypeNamesAreUniquePerTenant() throws Exception {
        createType("Truck");
        postJson("/api/v1/equipment-types", Map.of("name", "TRUCK"), token).andExpect(status().isConflict());
        JsonNode types = body(getJson("/api/v1/equipment-types", token).andExpect(status().isOk()));
        assertThat(types.size()).isEqualTo(1);
    }

    private static boolean containsId(JsonNode page, long id) {
        for (JsonNode item : page.path("items")) {
            if (item.path("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }
}
