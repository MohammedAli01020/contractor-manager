package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Tenant isolation, lifecycle, and optimistic concurrency for the Workforce item catalog. */
class WorkforceFinancialItemDefinitionIntegrationTest extends AbstractIntegrationTest {

    @Test
    void tenantCanCreateListRenameAndDeactivateItsOwnItems() throws Exception {
        String token = registerTenant("Financial Items Co", uniqueName("financialitems"))
                .path("accessToken").asText();

        JsonNode created = body(postJson("/api/v1/workforce/financial-item-definitions",
                request("TRANSPORT_ALLOWANCE", "بدل انتقال"), token)
                .andExpect(status().isCreated()));

        long id = created.path("id").asLong();
        assertThat(created.path("itemCode").asText()).isEqualTo("TRANSPORT_ALLOWANCE");
        assertThat(created.path("family").asText()).isEqualTo("EARNING");
        assertThat(created.path("calculationMethod").asText()).isEqualTo("PER_DAY");
        assertThat(created.path("active").asBoolean()).isTrue();

        Map<String, Object> update = new HashMap<>();
        update.put("version", created.path("version").asLong());
        update.put("displayName", "بدل انتقال يومي");
        update.put("description", "بدل انتقال عن كل يوم عمل معتمد");
        update.put("allowRecurring", true);
        update.put("allowOneOff", true);
        update.put("requiresApproval", true);

        JsonNode updated = body(putJson("/api/v1/workforce/financial-item-definitions/" + id,
                update, token).andExpect(status().isOk()));
        assertThat(updated.path("displayName").asText()).isEqualTo("بدل انتقال يومي");
        assertThat(updated.path("itemCode").asText()).isEqualTo("TRANSPORT_ALLOWANCE");
        assertThat(updated.path("version").asLong()).isGreaterThan(created.path("version").asLong());

        JsonNode deactivated = body(mockMvc.perform(post(
                "/api/v1/workforce/financial-item-definitions/" + id + "/deactivate")
                .param("version", updated.path("version").asText())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(deactivated.path("active").asBoolean()).isFalse();

        JsonNode activeOnly = body(mockMvc.perform(get("/api/v1/workforce/financial-item-definitions")
                .param("active", "true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()));
        assertThat(activeOnly.size()).isZero();

        JsonNode all = body(getJson("/api/v1/workforce/financial-item-definitions", token)
                .andExpect(status().isOk()));
        assertThat(all.size()).isEqualTo(1);
    }

    @Test
    void itemCodesAreUniquePerTenantButCanBeReusedByAnotherTenant() throws Exception {
        String tokenA = registerTenant("Financial Items A", uniqueName("financiala"))
                .path("accessToken").asText();
        String tokenB = registerTenant("Financial Items B", uniqueName("financialb"))
                .path("accessToken").asText();

        JsonNode itemA = body(postJson("/api/v1/workforce/financial-item-definitions",
                request("MEAL_ALLOWANCE", "بدل وجبة"), tokenA)
                .andExpect(status().isCreated()));

        postJson("/api/v1/workforce/financial-item-definitions",
                request("MEAL_ALLOWANCE", "وجبة إضافية"), tokenA)
                .andExpect(status().isConflict());

        JsonNode itemB = body(postJson("/api/v1/workforce/financial-item-definitions",
                request("MEAL_ALLOWANCE", "بدل وجبة"), tokenB)
                .andExpect(status().isCreated()));

        assertThat(itemB.path("id").asLong()).isNotEqualTo(itemA.path("id").asLong());
        getJson("/api/v1/workforce/financial-item-definitions/" + itemA.path("id").asLong(), tokenB)
                .andExpect(status().isNotFound());
    }

    @Test
    void staleVersionCannotOverwriteConcurrentMetadataUpdate() throws Exception {
        String token = registerTenant("Financial Items Version", uniqueName("financialversion"))
                .path("accessToken").asText();
        JsonNode created = body(postJson("/api/v1/workforce/financial-item-definitions",
                request("PRODUCTION_BONUS", "مكافأة إنتاج"), token)
                .andExpect(status().isCreated()));

        Map<String, Object> update = new HashMap<>();
        update.put("version", created.path("version").asLong());
        update.put("displayName", "مكافأة إنتاج معدلة");
        update.put("description", "نسخة أحدث");
        update.put("allowRecurring", false);
        update.put("allowOneOff", true);
        update.put("requiresApproval", true);
        putJson("/api/v1/workforce/financial-item-definitions/" + created.path("id").asLong(),
                update, token).andExpect(status().isOk());

        update.put("displayName", "تعديل قديم");
        putJson("/api/v1/workforce/financial-item-definitions/" + created.path("id").asLong(),
                update, token).andExpect(status().isConflict());
    }

    private Map<String, Object> request(String code, String name) {
        Map<String, Object> request = new HashMap<>();
        request.put("itemCode", code);
        request.put("displayName", name);
        request.put("description", "بند مالي قابل للتخصيص");
        request.put("family", "EARNING");
        request.put("calculationMethod", "PER_DAY");
        request.put("allowRecurring", false);
        request.put("allowOneOff", true);
        request.put("requiresApproval", true);
        return request;
    }
}
