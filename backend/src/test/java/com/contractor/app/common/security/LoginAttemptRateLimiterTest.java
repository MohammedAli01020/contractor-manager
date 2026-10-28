package com.contractor.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contractor.app.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LoginAttemptRateLimiterTest {

    @Test
    void throttlesTheSixthFailureForSameIdentityAndIp() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkAllowed("192.0.2.10", "Admin");
            limiter.recordFailure("192.0.2.10", "Admin", "tenant_login");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("192.0.2.10", "Admin"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void successfulLoginResetsIdentityFailures() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure("192.0.2.11", "Admin", "tenant_login");
        }
        limiter.recordSuccess("192.0.2.11", "Admin", "tenant_login");

        limiter.checkAllowed("192.0.2.11", "Admin");
    }

    @Test
    void identityLimitIsScopedToClientIp() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure("192.0.2.12", "Admin", "tenant_login");
        }

        limiter.checkAllowed("192.0.2.13", "Admin");
    }
}
