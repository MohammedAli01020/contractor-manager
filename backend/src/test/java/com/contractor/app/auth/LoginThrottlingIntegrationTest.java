package com.contractor.app.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contractor.app.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies that the controller applies the shared login throttling guard. */
class LoginThrottlingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void repeatedInvalidTenantLoginsReturnTooManyRequests() throws Exception {
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("username", uniqueName("throttle"));
        credentials.put("password", "WrongPass123");

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
        Map<String, Object> credentials = new HashMap<>();
        credentials.put("username", uniqueName("platform-throttle"));
        credentials.put("password", "WrongPass123");

        for (int attempt = 0; attempt < 5; attempt++) {
            postJson("/api/v1/platform/auth/login", credentials, null)
                    .andExpect(status().isUnauthorized());
        }

        postJson("/api/v1/platform/auth/login", credentials, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

}
