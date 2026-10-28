package com.contractor.app.common.security;

import com.contractor.app.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
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
    private static final int DEFAULT_MAX_IDENTITY_BUCKETS = 50_000;
    private static final int DEFAULT_MAX_IP_BUCKETS = 10_000;

    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> identityBuckets = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxIdentityBuckets;
    private final int maxIpBuckets;
    private final AtomicLong lastPruneAt = new AtomicLong();

    public LoginAttemptRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginAttemptRateLimiter(Clock clock) {
        this(clock, DEFAULT_MAX_IDENTITY_BUCKETS, DEFAULT_MAX_IP_BUCKETS);
    }

    LoginAttemptRateLimiter(Clock clock, int maxIdentityBuckets, int maxIpBuckets) {
        if (maxIdentityBuckets < 1 || maxIpBuckets < 1) {
            throw new IllegalArgumentException("Bucket limits must be positive");
        }
        this.clock = clock;
        this.maxIdentityBuckets = maxIdentityBuckets;
        this.maxIpBuckets = maxIpBuckets;
    }

    public void checkAllowed(String clientIp, String username) {
        long now = clock.millis();
        pruneExpiredPeriodically(now);
        Bucket ip = ipBuckets.get(ipKey(clientIp));
        Bucket identity = identityBuckets.get(identityKey(clientIp, username));
        if (isLimited(ip, now, MAX_FAILURES_PER_IP)
                || isLimited(identity, now, MAX_FAILURES_PER_IDENTITY_AND_IP)) {
            rejectThrottled();
        }
    }

    public void recordFailure(String clientIp, String username, String endpoint) {
        long now = clock.millis();
        increment(ipBuckets, ipKey(clientIp), now, maxIpBuckets);
        increment(identityBuckets, identityKey(clientIp, username), now, maxIdentityBuckets);
        log.warn("Security event=login_failed requestId={} endpoint={} status=401",
                MDC.get("requestId"), endpoint);
    }

    public void recordSuccess(String clientIp, String username, String endpoint) {
        identityBuckets.remove(identityKey(clientIp, username));
        log.info("Security event=login_succeeded requestId={} endpoint={} status=200",
                MDC.get("requestId"), endpoint);
    }

    private boolean isLimited(Bucket bucket, long now, int limit) {
        return bucket != null && bucket.isLimited(now, limit);
    }

    private void increment(ConcurrentHashMap<String, Bucket> target, String key, long now,
                           int maxBuckets) {
        synchronized (target) {
            Bucket current = target.get(key);
            if (current != null && !current.isExpired(now)) {
                current.increment();
                return;
            }
            if (current != null) {
                target.remove(key, current);
            }
            removeExpired(target, now);
            if (target.size() >= maxBuckets) {
                evictOldest(target);
            }
            target.put(key, new Bucket(now, 1));
        }
    }

    private void evictOldest(ConcurrentHashMap<String, Bucket> target) {
        String oldestKey = null;
        long oldestStart = Long.MAX_VALUE;
        for (Map.Entry<String, Bucket> entry : target.entrySet()) {
            long startedAt = entry.getValue().windowStartedAt();
            if (startedAt < oldestStart) {
                oldestStart = startedAt;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            target.remove(oldestKey);
        }
    }

    private void rejectThrottled() {
        log.warn("Security event=login_throttled requestId={} endpoint=login status=429",
                MDC.get("requestId"));
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                "محاولات تسجيل الدخول كثيرة. حاول مرة أخرى بعد 15 دقيقة.");
    }

    private void pruneExpiredPeriodically(long now) {
        long previous = lastPruneAt.get();
        if (now - previous >= PRUNE_INTERVAL_MILLIS
                && lastPruneAt.compareAndSet(previous, now)) {
            removeExpired(ipBuckets, now);
            removeExpired(identityBuckets, now);
        }
    }

    private void removeExpired(ConcurrentHashMap<String, Bucket> target, long now) {
        target.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private String ipKey(String clientIp) {
        return safeIp(clientIp);
    }

    private String identityKey(String clientIp, String username) {
        return safeIp(clientIp) + ":" + sha256(
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

        private long windowStartedAt() {
            return windowStartedAt;
        }
    }
}
