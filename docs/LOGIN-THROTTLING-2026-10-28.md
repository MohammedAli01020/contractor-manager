# Login throttling and security events — 2026-10-28

## Implementation on `feature/request-tracking`

- Added `LoginAttemptRateLimiter` for both `POST /api/v1/auth/login` and `POST /api/v1/platform/auth/login`.
- A username/IP pair is throttled after 5 failed login attempts within 15 minutes. A client IP is also capped at 100 failed login attempts within the same window, limiting easy bypass by changing usernames.
- A successful login clears the failure counter for that username/IP pair. The IP-wide counter remains until its window expires.
- Throttled requests return HTTP 429 with code `TOO_MANY_REQUESTS`.
- The username is SHA-256 hashed in the in-memory identity key. Authentication logs include the request ID from MDC, endpoint label, event, and status; they do not log the username, password, or client IP.
- Existing tenant-user dummy BCrypt verification and Platform Owner MFA challenge/setup behavior are unchanged. Existing TOTP lockout logic is untouched.
- Added unit tests for the per-identity threshold, reset after successful login, and separation between client IPs.

## Important deployment limitations

- The current limiter is **in-memory and per JVM**. It is appropriate as an initial single-instance guard, but it is not cluster-wide and resets when the process restarts. Before running multiple backend replicas, move counters to a shared atomic store such as Redis.
- The limiter uses the servlet remote address and intentionally does not trust arbitrary `X-Forwarded-For` values. If deployed behind a reverse proxy, configure a trusted-proxy strategy before enabling client-IP-based limits; otherwise requests may appear to originate from the proxy address.
- This increment has not been verified by a local Maven test run or CI yet. Do not describe the new code as passing until verification output is available.
- No database schema, Flyway migration, deployment, or merge to `main` was made as part of this increment.
