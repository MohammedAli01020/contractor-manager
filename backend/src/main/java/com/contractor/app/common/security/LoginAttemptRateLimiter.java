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
    private final Object reservationLock = new Object();

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

    /**
     * Reserves a failure slot under a short lock, then lets password verification run
     * without holding a lock. In-flight attempts count against both limits so a burst
     * cannot all pass the threshold check before failures are recorded.
     */
    public Attempt beginAttempt(String clientIp, String username) {
        synchronized (reservationLock) {
            long now = clock.millis();
            pruneExpiredPeriodically(now);
            String ipKey = ipKey(clientIp);
            String identityKey = identityKey(clientIp, username);
            Bucket ip = getOrCreateBucket(ipBuckets, ipKey, now, maxIpBuckets);
            Bucket identity = getOrCreateBucket(identityBuckets, identityKey, now, maxIdentityBuckets);
            if (ip.isLimitedIncludingInFlight(now, MAX_FAILURES_PER_IP)
                    || identity.isLimitedIncludingInFlight(now, MAX_FAILURES_PER_IDENTITY_AND_IP)) {
                rejectThrottled();
            }
            ip.reserve();
            identity.reserve();
            return new Attempt(ip, identity);
        }
    }

    private Bucket getOrCreateBucket(ConcurrentHashMap<String, Bucket> target, String key,
                                     long now, int maxBuckets) {
        Bucket current = target.get(key);
        if (current != null && !current.isExpired(now)) {
            return current;
        }
        if (current != null) {
            target.remove(key, current);
        }
        if (target.size() >= maxBuckets) {
            removeExpired(target, now);
            if (target.size() >= maxBuckets && !evictOldest(target)) {
                rejectThrottled();
            }
        }
        Bucket created = new Bucket(now, 0);
        target.put(key, created);
        return created;
    }

    public final class Attempt implements AutoCloseable {
        private final Bucket ip;
        private final Bucket identity;
        private boolean completed;

        private Attempt(Bucket ip, Bucket identity) {
            this.ip = ip;
            this.identity = identity;
        }

        public void failure(String endpoint) {
            synchronized (reservationLock) {
                if (completed) return;
                ip.completeFailure();
                identity.completeFailure();
                completed = true;
            }
            log.warn("Security event=login_failed requestId={} endpoint={} status=401",
                    MDC.get("requestId"), endpoint);
        }

        public void success(String endpoint) {
            synchronized (reservationLock) {
                if (completed) return;
                ip.releaseReservation();
                identity.completeSuccess();
                completed = true;
            }
            log.info("Security event=login_succeeded requestId={} endpoint={} status=200",
                    MDC.get("requestId"), endpoint);
        }

        @Override
        public void close() {
            synchronized (reservationLock) {
                if (completed) return;
                ip.releaseReservation();
                identity.releaseReservation();
                completed = true;
            }
        }
    }

    private boolean evictOldest(ConcurrentHashMap<String, Bucket> target) {
        String oldestKey = null;
        long oldestStart = Long.MAX_VALUE;
        for (Map.Entry<String, Bucket> entry : target.entrySet()) {
            Bucket bucket = entry.getValue();
            long startedAt = bucket.windowStartedAt();
            if (!bucket.hasReservations() && startedAt < oldestStart) {
                oldestStart = startedAt;
                oldestKey = entry.getKey();
            }
        }
        return oldestKey != null && target.remove(oldestKey) != null;
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
        private int inFlight;

        private Bucket(long windowStartedAt, int failures) {
            this.windowStartedAt = windowStartedAt;
            this.failures = failures;
        }

        private synchronized boolean isLimitedIncludingInFlight(long now, int limit) {
            return !isExpired(now) && failures + inFlight >= limit;
        }

        private synchronized boolean isExpired(long now) {
            return inFlight == 0 && now - windowStartedAt >= WINDOW_MILLIS;
        }

        private synchronized void reserve() {
            inFlight++;
        }

        private synchronized boolean hasReservations() {
            return inFlight > 0;
        }

        private synchronized void releaseReservation() {
            if (inFlight > 0) inFlight--;
        }

        private synchronized void completeFailure() {
            if (inFlight > 0) inFlight--;
            failures++;
        }

        private synchronized void completeSuccess() {
            if (inFlight > 0) inFlight--;
            failures = 0;
        }

        private long windowStartedAt() {
            return windowStartedAt;
        }
    }
}
