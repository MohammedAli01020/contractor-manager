package com.contractor.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * اختبارات تكامل على MySQL 8 حقيقي عبر Docker (Testcontainers).
 * حاوية واحدة تُشارك بين كل الاختبارات. إن لم يكن Docker متاحًا تُتخطّى الاختبارات ولا تفشل.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Passw0rdTest";

    private static final MySQLContainer<?> MYSQL;

    static {
        MySQLContainer<?> container = null;
        if (DockerClientFactory.instance().isDockerAvailable()) {
            container = new MySQLContainer<>("mysql:8.0")
                    .withDatabaseName("contractor_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--log-bin-trust-function-creators=1");
            container.start();
        }
        MYSQL = container;
    }

    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(MYSQL != null, "Docker غير متاح: تم تخطي اختبارات التكامل");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (MYSQL != null) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        }
        registry.add("app.jwt.secret", () -> "integration-test-secret-integration-test-123");
        registry.add("app.registration.enabled", () -> "true");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static String uniqueName(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected ResultActions postJson(String path, Object body, String accessToken) throws Exception {
        MockHttpServletRequestBuilder builder = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        return perform(builder, accessToken);
    }

    protected ResultActions putJson(String path, Object body, String accessToken) throws Exception {
        MockHttpServletRequestBuilder builder = put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        return perform(builder, accessToken);
    }

    protected ResultActions getJson(String path, String accessToken) throws Exception {
        return perform(get(path), accessToken);
    }

    protected ResultActions deleteJson(String path, String accessToken) throws Exception {
        return perform(delete(path), accessToken);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder, String accessToken) throws Exception {
        if (accessToken != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return mockMvc.perform(builder);
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected Map<String, Object> registerBody(String tenantName, String username) {
        Map<String, Object> map = new HashMap<>();
        map.put("tenantName", tenantName);
        map.put("fullName", "Owner " + username);
        map.put("username", username);
        map.put("password", PASSWORD);
        return map;
    }

    /** يسجّل مقاولًا جديدًا ويعيد استجابة الدخول (رموز + بيانات المستخدم). */
    protected JsonNode registerTenant(String tenantName, String username) throws Exception {
        return body(postJson("/api/v1/auth/register-tenant", registerBody(tenantName, username), null)
                .andExpect(status().isCreated()));
    }

    protected Map<String, Object> userBody(String username) {
        Map<String, Object> map = new HashMap<>();
        map.put("fullName", "Staff " + username);
        map.put("username", username);
        map.put("password", PASSWORD);
        return map;
    }
}
