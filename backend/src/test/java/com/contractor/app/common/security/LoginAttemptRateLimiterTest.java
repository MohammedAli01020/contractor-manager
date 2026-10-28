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
            assertAllowed(limiter, "192.0.2.10", "Admin");
            recordFailure(limiter, "192.0.2.10", "Admin", "tenant_login");
        }

        assertThatThrownBy(() -> assertAllowed(limiter, "192.0.2.10", "Admin"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void successfulLoginResetsIdentityFailures() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            recordFailure(limiter, "192.0.2.11", "Admin", "tenant_login");
        }
        recordSuccess(limiter, "192.0.2.11", "Admin", "tenant_login");

        assertAllowed(limiter, "192.0.2.11", "Admin");
    }

    @Test
    void identityLimitIsScopedToClientIp() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 5; attempt++) {
            recordFailure(limiter, "192.0.2.12", "Admin", "tenant_login");
        }

        assertAllowed(limiter, "192.0.2.13", "Admin");
    }

    @Test
    void identityLimitExpiresAfterFifteenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-28T08:00:00Z"));
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(clock);

        for (int attempt = 0; attempt < 5; attempt++) {
            recordFailure(limiter, "192.0.2.14", "Admin", "tenant_login");
        }
        assertThatThrownBy(() -> assertAllowed(limiter, "192.0.2.14", "Admin"))
                .isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(15).plusMillis(1));
        assertAllowed(limiter, "192.0.2.14", "Admin");
    }

    @Test
    void throttlesIpAfterOneHundredFailuresAcrossDifferentUsernames() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 100; attempt++) {
            recordFailure(limiter, "192.0.2.15", "user" + attempt, "tenant_login");
        }

        assertThatThrownBy(() -> assertAllowed(limiter, "192.0.2.15", "another-user"))
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
                executor.submit(() -> recordFailure(limiter, 
                        "192.0.2.16", "ConcurrentAdmin", "tenant_login"));
            }
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThatThrownBy(() -> assertAllowed(limiter, "192.0.2.16", "ConcurrentAdmin"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }


    @Test
    void evictsOldestIdentityBucketInsteadOfRejectingNewIdentitiesAtCapacity() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC), 2, 2);

        recordFailure(limiter, "192.0.2.20", "first", "tenant_login");
        recordFailure(limiter, "192.0.2.20", "second", "tenant_login");
        recordFailure(limiter, "192.0.2.20", "third", "tenant_login");

        // New identities remain serviceable at capacity, and the IP-wide bucket is preserved.
        assertAllowed(limiter, "192.0.2.20", "fourth");
        for (int attempt = 0; attempt < 97; attempt++) {
            recordFailure(limiter, "192.0.2.20", "ip-user-" + attempt, "tenant_login");
        }
        assertThatThrownBy(() -> assertAllowed(limiter, "192.0.2.20", "last-user"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void prunesExpiredBucketsAtCapacityWithoutEvictingActiveIdentity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-28T08:00:00Z"));
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(clock, 2, 2);

        recordFailure(limiter, "192.0.2.40", "expired-user", "tenant_login");
        clock.advance(Duration.ofMinutes(14));
        recordFailure(limiter, "192.0.2.41", "active-user", "tenant_login");

        // The first bucket has expired; the second is still active when capacity is reached.
        clock.advance(Duration.ofMinutes(1).plusMillis(1));
        recordFailure(limiter, "192.0.2.42", "new-user", "tenant_login");

        for (int attempt = 0; attempt < 4; attempt++) {
            recordFailure(limiter, "192.0.2.41", "active-user", "tenant_login");
        }

        assertThatThrownBy(() -> assertAllowed(limiter, "192.0.2.41", "active-user"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void reservationsPreventConcurrentIdentityAttemptsFromPassingTheThreshold() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));
        java.util.List<LoginAttemptRateLimiter.Attempt> attempts = new java.util.ArrayList<>();

        try {
            for (int attempt = 0; attempt < 5; attempt++) {
                attempts.add(limiter.beginAttempt("192.0.2.50", "same-user"));
            }

            assertThatThrownBy(() -> limiter.beginAttempt("192.0.2.50", "same-user"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).getStatus())
                            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

            attempts.remove(0).close();
            attempts.add(limiter.beginAttempt("192.0.2.50", "same-user"));
        } finally {
            attempts.forEach(LoginAttemptRateLimiter.Attempt::close);
        }
    }

    @Test
    void reservationsEnforceIpLimitAcrossDifferentConcurrentUsernames() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter(
                Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneOffset.UTC));
        java.util.List<LoginAttemptRateLimiter.Attempt> attempts = new java.util.ArrayList<>();

        try {
            for (int attempt = 0; attempt < 100; attempt++) {
                attempts.add(limiter.beginAttempt("192.0.2.51", "user-" + attempt));
            }

            assertThatThrownBy(() -> limiter.beginAttempt("192.0.2.51", "another-user"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).getStatus())
                            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        } finally {
            attempts.forEach(LoginAttemptRateLimiter.Attempt::close);
        }
    }

    @Test
    void bucketCapacityIsValidated() {
        assertThatThrownBy(() -> new LoginAttemptRateLimiter(
                Clock.systemUTC(), 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertAllowed(LoginAttemptRateLimiter limiter, String clientIp, String username) {
        try (LoginAttemptRateLimiter.Attempt ignored = limiter.beginAttempt(clientIp, username)) {
            // Closing without completing releases the reservation, mirroring an abandoned attempt.
        }
    }

    private static void recordFailure(LoginAttemptRateLimiter limiter, String clientIp,
                                      String username, String endpoint) {
        try (LoginAttemptRateLimiter.Attempt attempt = limiter.beginAttempt(clientIp, username)) {
            attempt.failure(endpoint);
        }
    }

    private static void recordSuccess(LoginAttemptRateLimiter limiter, String clientIp,
                                      String username, String endpoint) {
        try (LoginAttemptRateLimiter.Attempt attempt = limiter.beginAttempt(clientIp, username)) {
            attempt.success(endpoint);
        }
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
