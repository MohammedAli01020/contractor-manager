package com.contractor.app.common.security;

import com.contractor.app.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Per-process login throttling. Multiple backend instances need a shared store
 * (for example Redis) before this protection can be considered cluster-wide.
 */
@Component
public class LoginAttemptRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptRateLimiter.class);
    private static final int MAX_FAILURES_PER_IDENTITY_AND_IP = 5;
    private static final int MAX_FAILURES_PER_IP = 100;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(15).toMillis();
    private static final long PRUNE_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();
    private static final int MAX_BUCKETS = 50_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final AtomicLong lastPruneAt = new AtomicLong();

    public LoginAttemptRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String clientIp, String username) {
        long now = clock.millis();
        pruneExpired(now);
        Bucket ip = buckets.get(ipKey(clientIp));
        Bucket identity = buckets.get(identityKey(clientIp, username));
        if (isLimited(ip, now, MAX_FAILURES_PER_IP)
                || isLimited(identity, now, MAX_FAILURES_PER_IDENTITY_AND_IP)) {
            log.warn("Security event=login_throttled requestId={} endpoint=login status=429",
                    MDC.get("requestId"));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "محاولات تسجيل الدخول كثيرة. حاول مرة أخرى بعد 15 دقيقة.");
        }
    }

    public void recordFailure(String clientIp, String username, String endpoint) {
        long now = clock.millis();
        increment(ipKey(clientIp), now);
        increment(identityKey(clientIp, username), now);
        log.warn("Security event=login_failed requestId={} endpoint={} status=401",
                MDC.get("requestId"), endpoint);
    }

    public void recordSuccess(String clientIp, String username, String endpoint) {
        buckets.remove(identityKey(clientIp, username));
        log.info("Security event=login_succeeded requestId={} endpoint={} status=200",
                MDC.get("requestId"), endpoint);
    }

    private boolean isLimited(Bucket bucket, long now, int limit) {
        return bucket != null && bucket.isLimited(now, limit);
    }

    private void increment(String key, long now) {
        buckets.compute(key, (ignored, current) -> {
            if (current == null || current.isExpired(now)) {
                return new Bucket(now, 1);
            }
            current.increment();
            return current;
        });
    }

    private void pruneExpired(long now) {
        buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private String ipKey(String clientIp) {
        return "ip:" + safeIp(clientIp);
    }

    private String identityKey(String clientIp, String username) {
        return "identity:" + safeIp(clientIp) + ":" + sha256(
                username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private String safeIp(String clientIp) {
        return clientIp == null || clientIp.trim().isEmpty() ? "unknown" : clientIp;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(Character.forDigit((item >> 4) & 0xF, 16));
                result.append(Character.forDigit(item & 0xF, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static final class Bucket {
        private final long windowStartedAt;
        private int failures;

        private Bucket(long windowStartedAt, int failures) {
            this.windowStartedAt = windowStartedAt;
            this.failures = failures;
        }

        private synchronized boolean isLimited(long now, int limit) {
            return !isExpired(now) && failures >= limit;
        }

        private synchronized boolean isExpired(long now) {
            return now - windowStartedAt >= WINDOW_MILLIS;
        }

        private synchronized void increment() {
            failures++;
        }
    }
}
