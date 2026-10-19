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

/** المقاول (ب) لا يرى ولا يعدّل ولا يحذف ولا يربط بسجلات المقاول (أ). */
class MasterDataTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private String tokenA;
    private String tokenB;
    private long siteA;
    private long driverA;
    private long typeA;
    private long equipmentA;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = registerTenant("Iso A", uniqueName("owa")).path("accessToken").asText();
        tokenB = registerTenant("Iso B", uniqueName("owb")).path("accessToken").asText();

        siteA = body(postJson("/api/v1/sites", Map.of("name", "A Site"), tokenA)
                .andExpect(status().isCreated())).path("id").asLong();

        Map<String, Object> driver = new HashMap<>();
        driver.put("name", "A Driver");
        driver.put("workerType", "DRIVER");
        driver.put("morningRate", new BigDecimal("100"));
        driver.put("eveningRate", new BigDecimal("100"));
        driver.put("nightRate", new BigDecimal("100"));
        driverA = body(postJson("/api/v1/drivers", driver, tokenA).andExpect(status().isCreated()))
                .path("id").asLong();

        typeA = body(postJson("/api/v1/equipment-types", Map.of("name", "A Type"), tokenA)
                .andExpect(status().isCreated())).path("id").asLong();

        Map<String, Object> equipment = new HashMap<>();
        equipment.put("code", "A-01");
        equipment.put("equipmentTypeId", typeA);
        equipment.put("operatingCostPerShift", new BigDecimal("50"));
        equipmentA = body(postJson("/api/v1/equipment", equipment, tokenA).andExpect(status().isCreated()))
                .path("id").asLong();
    }

    @Test
    void listsOfAnotherTenantAreEmpty() throws Exception {
        assertThat(body(getJson("/api/v1/sites?includeInactive=true", tokenB)).path("totalElements").asLong())
                .isZero();
        assertThat(body(getJson("/api/v1/drivers?includeInactive=true", tokenB)).path("totalElements").asLong())
                .isZero();
        assertThat(body(getJson("/api/v1/equipment?includeInactive=true", tokenB)).path("totalElements").asLong())
                .isZero();
        assertThat(body(getJson("/api/v1/equipment-types?includeInactive=true", tokenB)).size()).isZero();

        // بحث بالاسم الصحيح لا يكشف سجل المقاول الآخر
        assertThat(body(getJson("/api/v1/sites?q=Site", tokenB)).path("totalElements").asLong()).isZero();
    }

    @Test
    void cannotReadAnotherTenantsRecordsById() throws Exception {
        getJson("/api/v1/sites/" + siteA, tokenB).andExpect(status().isNotFound());
        getJson("/api/v1/drivers/" + driverA, tokenB).andExpect(status().isNotFound());
        getJson("/api/v1/equipment/" + equipmentA, tokenB).andExpect(status().isNotFound());
    }

    @Test
    void cannotModifyOrDeleteAnotherTenantsRecords() throws Exception {
        Map<String, Object> siteUpdate = new HashMap<>();
        siteUpdate.put("name", "Hijacked");
        siteUpdate.put("version", 0);
        putJson("/api/v1/sites/" + siteA, siteUpdate, tokenB).andExpect(status().isNotFound());

        deleteJson("/api/v1/sites/" + siteA, tokenB).andExpect(status().isNotFound());
        deleteJson("/api/v1/drivers/" + driverA, tokenB).andExpect(status().isNotFound());
        deleteJson("/api/v1/equipment/" + equipmentA, tokenB).andExpect(status().isNotFound());
        deleteJson("/api/v1/equipment-types/" + typeA, tokenB).andExpect(status().isNotFound());

        // كل شيء عند صاحبه سليم
        getJson("/api/v1/sites/" + siteA, tokenA).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.name").value("A Site"));
        getJson("/api/v1/drivers/" + driverA, tokenA).andExpect(jsonPath("$.active").value(true));
        getJson("/api/v1/equipment/" + equipmentA, tokenA).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void cannotAttachEquipmentToAnotherTenantsType() throws Exception {
        Map<String, Object> equipment = new HashMap<>();
        equipment.put("code", "B-01");
        equipment.put("equipmentTypeId", typeA); // نوع يخص المقاول (أ)
        equipment.put("operatingCostPerShift", new BigDecimal("10"));

        postJson("/api/v1/equipment", equipment, tokenB).andExpect(status().isNotFound());

        // وبنوعه الخاص ينجح
        long typeB = body(postJson("/api/v1/equipment-types", Map.of("name", "B Type"), tokenB)
                .andExpect(status().isCreated())).path("id").asLong();
        equipment.put("equipmentTypeId", typeB);
        postJson("/api/v1/equipment", equipment, tokenB).andExpect(status().isCreated());
    }

    @Test
    void tenantIdInBodyCannotMoveRecordsAcrossTenants() throws Exception {
        Map<String, Object> site = new HashMap<>();
        site.put("name", "B Injected");
        site.put("tenantId", 1);
        long id = body(postJson("/api/v1/sites", site, tokenB).andExpect(status().isCreated()))
                .path("id").asLong();

        getJson("/api/v1/sites/" + id, tokenB).andExpect(status().isOk());
        getJson("/api/v1/sites/" + id, tokenA).andExpect(status().isNotFound());
    }
}
