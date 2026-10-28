# Login throttling and security events — 2026-10-28

## Implementation on `feature/request-tracking`

- Added `LoginAttemptRateLimiter` for both `POST /api/v1/auth/login` and `POST /api/v1/platform/auth/login`.
- A username/IP pair is throttled after 5 failed login attempts within 15 minutes. A client IP is also capped at 100 failed login attempts within the same window, limiting easy bypass by changing usernames.
- A successful login clears the failure counter for that username/IP pair. The IP-wide counter remains until its window expires.
- Throttled requests return HTTP 429 with code `TOO_MANY_REQUESTS`.
- The username is SHA-256 hashed in the in-memory identity key. Authentication logs include the request ID from MDC, endpoint label, event, and status; they do not log the username, password, or client IP.
- Existing tenant-user dummy BCrypt verification and Platform Owner MFA challenge/setup behavior are unchanged. Existing TOTP lockout logic is untouched.
- Added unit tests for the per-identity threshold, reset after successful login, separation between client IPs, expiry after 15 minutes, the 100-failure IP ceiling, and concurrent failures. Added integration coverage for both tenant and platform login throttling, including concurrent HTTP requests asserting exactly five authentication attempts and HTTP 429 for the remainder.
- Expired buckets are swept at most once per minute instead of scanning the maps on every login check. Identity counters and IP counters are stored separately with independent caps (50,000 identity buckets and 10,000 IP buckets); when a cap is reached, the oldest bucket is evicted instead of broadly rejecting new identities. This bounds memory but means high-cardinality abuse can evict older counters, so shared-store/rate-limit design still needs review before production.
- Platform login performs a dummy BCrypt password check when an account is missing or inactive, reducing the username-enumeration timing difference. The platform MFA challenge and TOTP behavior remain unchanged.

## Important deployment limitations

- The current limiter is **in-memory and per JVM**. It is appropriate as an initial single-instance guard, but it is not cluster-wide and resets when the process restarts. Before running multiple backend replicas, move counters to a shared atomic store such as Redis.
- The limiter uses the servlet remote address and intentionally does not trust arbitrary `X-Forwarded-For` values. If deployed behind a reverse proxy, configure a trusted-proxy strategy before enabling client-IP-based limits; otherwise requests may appear to originate from the proxy address.
- **Login concurrency reservation follow-up (code updated; verification pending):** both login controllers now reserve a slot under a short synchronized section, then perform password verification, database work, and token creation without holding that lock. In-flight reservations count toward the identity/IP thresholds, preventing a concurrent burst from passing the check before failures are recorded. A failed attempt converts its reservation into a failure; a successful attempt releases the IP reservation and clears the identity failure count; other exceptions release reservations through try-with-resources. Active reservations are not evicted to make room for new buckets. Under bucket-capacity pressure, a new attempt can receive HTTP 429 if no inactive bucket can be evicted. This removes the previous whole-login per-IP serialization, but it is not a benchmark and needs Maven/CI verification before acceptance.
- **CI verification (user-provided):** the Maven test step succeeded for IP-lock code commit `204332a1cb20d5a0424d8a5588f59255de36536b`. The subsequent concurrent HTTP integration tests also passed the Maven test step on commit `cea8748cbdd6cd47fb222d79ffe75e0cdbe72083` (user-provided result). These test results do not establish load/performance behavior or production deployment readiness.
- No database schema, Flyway migration, deployment, or merge to `main` was made as part of this increment.

## Concurrent login integration tests — verification update

- **Concurrent HTTP integration tests (user-provided CI result):** Maven test step **success** for commit `cea8748cbdd6cd47fb222d79ffe75e0cdbe72083`. The added tests send 12 simultaneous MockMvc requests to each login endpoint from a synthetic client IP and assert five HTTP 401 responses followed by seven HTTP 429 responses. This confirms the test step for that commit; it is not a load/performance benchmark or production deployment check.


## Bucket-pruning performance fix — CI verified (2026-10-28)

- **Code commit:** `f87cb0d54d81219c7f0238e71ab70a90ae2b2a82`; **test commit:** `5e82a0632f54ae018be2980c823609de57cd799d`.
- **Verification:** GitHub Actions [Backend CI run 40675857991](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40675857991) completed successfully for test commit `5e82a0632f54ae018be2980c823609de57cd799d`; the Maven test step was also confirmed successful by the user.
- **Change:** new bucket creation no longer scans the entire target map on every insertion. Expired-entry removal during insertion now runs only when that map reaches its configured capacity, before any oldest-entry eviction.
- **Test:** added a capacity-pressure test that verifies expired entries are reclaimed and an active identity bucket remains subject to the configured throttle.
- **Scope:** branch `feature/request-tracking` only. No database/Flyway changes, deployment, or merge to `main`.
- **Still outstanding:** no load benchmark was run. The per-IP lock still serializes complete login flows sharing the same lock stripe, and counters remain per-process.


## Short-lived reservation implementation — 2026-10-28

- Replaced controller-level IP/identity lock holding with `beginAttempt(...)` reservations in `LoginAttemptRateLimiter`.
- Added unit coverage for five simultaneous reservations for one identity and the 100-reservation IP-wide ceiling across different usernames, including releasing an unfinished reservation.
- Removed tests for the old lock-stripe approach. Existing HTTP concurrency integration tests still cover both login endpoints.
- **Verification status:** GitHub workflow-run lookup did not return a run for the latest test commit during this update. Do not treat the code as CI-verified until a Maven test run succeeds.
- No database/Flyway changes, deployment, or merge to `main`.
