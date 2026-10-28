package com.contractor.app.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Verifies login throttling at the HTTP/controller boundary, including concurrent requests. */
class LoginThrottlingIntegrationTest extends AbstractIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 12;

    @Test
    void repeatedInvalidTenantLoginsReturnTooManyRequests() throws Exception {
        Map<String, Object> credentials = credentials("throttle");

        for (int attempt = 0; attempt < 5; attempt++) {
            postJson("/api/v1/auth/login", credentials, null)
                    .andExpect(status().isUnauthorized());
        }

        postJson("/api/v1/auth/login", credentials, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void repeatedInvalidPlatformLoginsReturnTooManyRequests() throws Exception {
        Map<String, Object> credentials = credentials("platform-throttle");

        for (int attempt = 0; attempt < 5; attempt++) {
            postJson("/api/v1/platform/auth/login", credentials, null)
                    .andExpect(status().isUnauthorized());
        }

        postJson("/api/v1/platform/auth/login", credentials, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void concurrentInvalidTenantLoginsNeverExceedFiveAuthenticationAttempts() throws Exception {
        assertConcurrentAttemptsRespectLimit("/api/v1/auth/login", "concurrent-tenant");
    }

    @Test
    void concurrentInvalidPlatformLoginsNeverExceedFiveAuthenticationAttempts() throws Exception {
        assertConcurrentAttemptsRespectLimit("/api/v1/platform/auth/login", "concurrent-platform");
    }

    private Map<String, Object> credentials(String prefix) {
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("username", uniqueName(prefix));
        credentials.put("password", "WrongPass123");
        return credentials;
    }

    private void assertConcurrentAttemptsRespectLimit(String endpoint, String usernamePrefix)
            throws Exception {
        Map<String, Object> credentials = credentials(usernamePrefix);
        String body = objectMapper.writeValueAsString(credentials);
        // Use a fresh synthetic client IP so the shared IP-wide counter from other tests cannot affect this case.
        String clientIp = "2001:db8::" + UUID.randomUUID().toString().replace("-", "");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        List<Future<Integer>> responses = new ArrayList<>();

        try {
            for (int attempt = 0; attempt < CONCURRENT_ATTEMPTS; attempt++) {
                responses.add(executor.submit(() -> {
                    start.await();
                    MockHttpServletRequestBuilder request = post(endpoint)
                            .with(servletRequest -> {
                                servletRequest.setRemoteAddr(clientIp);
                                return servletRequest;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body);
                    return mockMvc.perform(request).andReturn().getResponse().getStatus();
                }));
            }

            start.countDown();
            int unauthorized = 0;
            int tooManyRequests = 0;
            for (Future<Integer> response : responses) {
                int statusCode = response.get(60, TimeUnit.SECONDS);
                if (statusCode == 401) {
                    unauthorized++;
                } else if (statusCode == 429) {
                    tooManyRequests++;
                } else {
                    throw new AssertionError("Unexpected HTTP status from " + endpoint + ": " + statusCode);
                }
            }

            assertThat(unauthorized)
                    .as("only the first five attempts should reach authentication for %s", endpoint)
                    .isEqualTo(5);
            assertThat(tooManyRequests)
                    .as("remaining concurrent attempts should be throttled for %s", endpoint)
                    .isEqualTo(CONCURRENT_ATTEMPTS - 5);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
