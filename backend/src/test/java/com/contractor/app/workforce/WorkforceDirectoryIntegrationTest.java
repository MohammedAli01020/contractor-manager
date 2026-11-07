package com.contractor.app.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** API contract tests for the canonical Workforce directory and legacy Driver mapping. */
class WorkforceDirectoryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void directoryReturnsExplicitCanonicalAndOperationalIdsWithoutCrossTenantLeakage() throws Exception {
        String tokenA = registerTenant("Directory Tenant A", uniqueName("directorya"))
                .path("accessToken").asText();
        String tokenB = registerTenant("Directory Tenant B", uniqueName("directoryb"))
                .path("accessToken").asText();

        long driverA = createDriver(tokenA, "Directory Worker A", "01011111111");
        createDriver(tokenB, "Directory Worker B", "01022222222");
        long memberA = jdbcMemberId(driverA);

        JsonNode pageA = body(getJson("/api/v1/workforce/members?search=Directory%20Worker", tokenA)
                .andExpect(status().isOk()));

        assertThat(pageA.path("totalElements").asLong()).isEqualTo(1);
        JsonNode item = pageA.path("content").get(0);
        assertThat(item.path("workforceMemberId").asLong()).isEqualTo(memberA);
        assertThat(item.path("driverId").asLong()).isEqualTo(driverA);
        assertThat(item.path("operationalProfileLinked").asBoolean()).isTrue();
        assertThat(item.path("fullName").asText()).isEqualTo("Directory Worker A");
        assertThat(item.has("nationalId")).isFalse();
    }

    @Test
    void directorySupportsSearchAndPagination() throws Exception {
        String token = registerTenant("Directory Paging", uniqueName("directorypage"))
                .path("accessToken").asText();
        createDriver(token, "Paging Worker Alpha", "01033333333");
        createDriver(token, "Paging Worker Beta", "01044444444");

        JsonNode firstPage = body(getJson(
                "/api/v1/workforce/members?search=Paging%20Worker&page=0&size=1", token)
                .andExpect(status().isOk()));

        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(2);
        assertThat(firstPage.path("totalPages").asLong()).isEqualTo(2);
        assertThat(firstPage.path("content").size()).isEqualTo(1);
    }

    private long createDriver(String token, String name, String phone) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name);
        request.put("workerType", "DRIVER");
        request.put("phone", phone);
        request.put("morningRate", new BigDecimal("100.00"));
        request.put("eveningRate", new BigDecimal("110.00"));
        request.put("nightRate", new BigDecimal("120.00"));
        return body(postJson("/api/v1/drivers", request, token)
                .andExpect(status().isCreated())).path("id").asLong();
    }

    private long jdbcMemberId(long driverId) {
        return jdbc.queryForObject(
                "SELECT workforce_member_id FROM drivers WHERE id = ?",
                Long.class, driverId);
    }
}
