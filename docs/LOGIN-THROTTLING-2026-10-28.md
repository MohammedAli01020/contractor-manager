# Login throttling and security events — 2026-10-28

## Implementation on `feature/request-tracking`

- Added `LoginAttemptRateLimiter` for both `POST /api/v1/auth/login` and `POST /api/v1/platform/auth/login`.
- A username/IP pair is throttled after 5 failed login attempts within 15 minutes. A client IP is also capped at 100 failed login attempts within the same window, limiting easy bypass by changing usernames.
- A successful login clears the failure counter for that username/IP pair. The IP-wide counter remains until its window expires.
- Throttled requests return HTTP 429 with code `TOO_MANY_REQUESTS`.
- The username is SHA-256 hashed in the in-memory identity key. Authentication logs include the request ID from MDC, endpoint label, event, and status; they do not log the username, password, or client IP.
- Existing tenant-user dummy BCrypt verification and Platform Owner MFA challenge/setup behavior are unchanged. Existing TOTP lockout logic is untouched.
- Added unit tests for the per-identity threshold, reset after successful login, separation between client IPs, expiry after 15 minutes, the 100-failure IP ceiling, and concurrent failures. Added integration coverage for both tenant and platform login throttling.
- Expired buckets are swept at most once per minute instead of scanning the maps on every login check. Identity counters and IP counters are stored separately with independent caps (50,000 identity buckets and 10,000 IP buckets); when a cap is reached, the oldest bucket is evicted instead of broadly rejecting new identities. This bounds memory but means high-cardinality abuse can evict older counters, so shared-store/rate-limit design still needs review before production.
- Platform login performs a dummy BCrypt password check when an account is missing or inactive, reducing the username-enumeration timing difference. The platform MFA challenge and TOTP behavior remain unchanged.

## Important deployment limitations

- The current limiter is **in-memory and per JVM**. It is appropriate as an initial single-instance guard, but it is not cluster-wide and resets when the process restarts. Before running multiple backend replicas, move counters to a shared atomic store such as Redis.
- The limiter uses the servlet remote address and intentionally does not trust arbitrary `X-Forwarded-For` values. If deployed behind a reverse proxy, configure a trusted-proxy strategy before enabling client-IP-based limits; otherwise requests may appear to originate from the proxy address.
- The login controllers serialize attempts for the same normalized username/IP identity using a fixed array of lock stripes, avoiding unbounded lock allocation and preventing simultaneous attempts for that same identity from all passing the failure check before results are recorded. Stripe collisions may serialize unrelated identities. The IP-wide counter can still be updated by concurrent requests for different usernames from the same IP; this should be considered in the remaining concurrency review.
- **CI verification (user-provided):** the Maven test step succeeded for code/test commit `cb7c18f4968a37f44174bb5ddfb433724b456527`. The subsequent branch-head commit `6cfa193e1b40ba948665940e818a2cfc7110edbf` is documentation-only. This records the reported Maven step result; it is not a claim of deployment or multi-instance verification.
- No database schema, Flyway migration, deployment, or merge to `main` was made as part of this increment.
