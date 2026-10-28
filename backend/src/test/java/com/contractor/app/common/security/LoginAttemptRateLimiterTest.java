package com.contractor.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contractor.app.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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

    @Test
    void identityLimitExpiresAfterFifteenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-28T08:00:00Z"));
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(clock);

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure("192.0.2.14", "Admin", "tenant_login");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("192.0.2.14", "Admin"))
                .isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(15).plusMillis(1));
        limiter.checkAllowed("192.0.2.14", "Admin");
    }

    @Test
    void throttlesIpAfterOneHundredFailuresAcrossDifferentUsernames() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 100; attempt++) {
            limiter.recordFailure("192.0.2.15", "user" + attempt, "tenant_login");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("192.0.2.15", "another-user"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void concurrentFailuresStillTriggerTheIdentityLimit() throws Exception {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            for (int attempt = 0; attempt < 40; attempt++) {
                executor.submit(() -> limiter.recordFailure(
                        "192.0.2.16", "ConcurrentAdmin", "tenant_login"));
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThatThrownBy(() -> limiter.checkAllowed("192.0.2.16", "ConcurrentAdmin"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;

        private MutableClock(Instant initial) {
            this.millis = new AtomicLong(initial.toEpochMilli());
        }

        private void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }

}
