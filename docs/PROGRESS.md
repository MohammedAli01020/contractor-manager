## Tenant suspension/reactivation — implementation added, verification pending (2026-10-26)

- **Branch:** `feature/platform-owner-web`; no changes to `main`, database contents, schema, or Flyway migrations.
- Added a Platform Owner-only backend endpoint: `PATCH /api/v1/platform/tenants/{tenantId}/status`, accepting only `ACTIVE` or `SUSPENDED`. It returns 404 for an unknown tenant and 400 for an invalid status. Existing `/api/v1/platform/**` authorization requires the `PLATFORM_OWNER` role.
- Added the service operation using the existing `TenantStatus` enum and `Tenant.setStatus`; no database migration is needed. The existing `AuthenticatedUserLoader` checks tenant status for protected requests, so suspension blocks tenant-user protected access and reactivation removes that tenant-status block (individual user disabled state still applies).
- Added a same-origin Next.js route proxy using the HttpOnly Platform Owner session cookie; the bearer token is not sent to browser JavaScript. Added details-page controls with confirmation, loading/error states, and in-memory list/detail refresh after success.
- Added an integration test covering suspend/reactivate, missing Platform Owner authentication, unknown tenant ID, and invalid status.
- **Verification pending:** GitHub source commits are present, but backend tests, frontend lint/build, and manual browser behavior have not yet been observed for this increment. Do not claim this feature is verified until checks pass.
- **Next local steps:** inspect `git status -sb` in `D:\\contractor-manager`; if the worktree is clean, run `git pull --ff-only origin feature/platform-owner-web`. Then run backend tests from `backend` (use the repo's existing Maven wrapper if present; otherwise `mvn test`) and run `npm run lint` plus `npm run build` from `platform-owner-web`. Preserve local edits if the worktree is not clean. Do not reset the database or merge to `main` without explicit approval.

---

## Latest UI increment — 2026-10-26 (tenant dashboard search and metrics)

- **Implemented on** `feature/platform-owner-web`: added search over company name, tenant ID, Admin full name, and Admin username; added a company status filter (all/active/suspended); added a no-matches state; added responsive summary cards for total companies, active companies, suspended companies, and Admins awaiting password activation.
- **Implementation is data-derived:** summary counts use the tenant list returned by the existing API; no fabricated metrics, backend changes, or database changes were introduced.
- **Commits:** app logic `f9790772789a0a1c56d99bf8368d4b34e7fe38f0`; responsive CSS `a4cbc571844576cdcf5bc42fad65d46a337ec007`.
- **Verification pending:** the user must fast-forward/pull the latest branch and run `npm run lint` and `npm run build`; no claim is made yet that these new changes pass locally. Then manually verify search, status filtering, counts, and empty-results behavior.
- **Safety remains unchanged:** keep Platform Owner and tenant Admin auth paths separate, do not touch/reset the existing database, and do not merge to `main` without explicit approval.

---

## Latest verified handoff — 2026-10-26 (Platform Owner UI local verification)

- **Platform Owner UI branch:** `feature/platform-owner-web`, remote HEAD verified as `4bd8f4e00c787d2db4d24b951fe4ad738ed8aa1e`. The user's local branch was fast-forwarded to this commit.
- **Local frontend verification (user-provided output):** in `D:\\contractor-manager\\platform-owner-web`, `npm ci` completed successfully (357 packages; audit reported 0 vulnerabilities), `npm run lint` completed with no reported errors, and `npm run build` completed successfully on Next.js 16.4.3 with TypeScript and static-page generation passing. The install emitted an advisory about the `unrs-resolver` postinstall script not being approved; this did not prevent installation/build.
- **End-to-end UI smoke check (user confirmed):** the app ran, Platform Owner login worked, and the dashboard workflow the user tested worked. This is user-confirmed manual verification, not an automated browser test.
- **Existing UI capabilities:** separate Platform Owner login; secure Next.js route handlers using an HttpOnly cookie; company list; company creation with first Admin; activation URL display/copy; Admin name/username, account active state, and password activation state; refresh list and logout.
- **Correct API route separation:** Platform Owner login is `POST /api/v1/platform/auth/login`; tenant Admin login is `POST /api/v1/auth/login`. Do not mix their sessions or endpoints.
- **Runtime:** Next.js at `http://localhost:3000`; backend host port `8081` (container port `8080`); MySQL host port `3308`. Do not stop the unrelated service on host port `8080`.
- **Database safety:** the user has existing data and confirmed the UI works. Do not reset/delete the database, rerun bootstrap against it, or edit applied Flyway migrations.
- **Next UI increment:** improve the existing company dashboard with a searchable/filterable company list and factual summary counts derived only from the returned tenant data (total companies, active/suspended companies, and Admins awaiting activation). Keep it frontend-only unless an actual API gap is found; do not invent metrics or modify backend/database behavior for cosmetic UI needs.
- **Git safety:** continue on `feature/platform-owner-web`; do not merge to `main` without explicit approval. The backend/bootstrap branch `feature/platform-owner-activation` is separate; do not assume its HEAD matches this branch.
- **Verification status:** only the above local lint/build/manual checks are confirmed for the current UI. Re-run `npm run lint` and `npm run build` after the next UI changes and record their actual outputs.

---

> This file is the handoff note for continuing work across ChatGPT conversations. Update it only after checking the actual repository state and recording verified results.


## Latest verified handoff — 2026-10-26 (login routing and Platform Owner UI)

- **Frontend branch:** `feature/platform-owner-web`, verified HEAD `86c7b1f660edf457c0de1903313f87f163d35c2a`. This branch contains the Next.js Platform Owner dashboard. Backend bootstrap/security work also exists on the separate `feature/platform-owner-activation` branch; do not assume the two branch heads are identical.
- **Local runtime verified by the user:** Next.js at `http://localhost:3000`; Docker backend host port is `8081` (container port 8080); MySQL host port is `3308`. The backend image built successfully and the API container restarted successfully. Do not stop the unrelated service bound to host port 8080.
- **Login issue resolved by correct endpoint selection:** tenant Admin `mali` was receiving HTTP 401 when trying to log in through `POST /api/v1/platform/auth/login`. The user confirmed login succeeded after switching to the tenant-user endpoint `POST /api/v1/auth/login`. The former endpoint is for Platform Owner only.
- **Frontend code review result:** `platform-owner-web/app/platform-owner-app.tsx` sends the Platform Owner login form to the same-origin Next.js route `/api/platform/auth/login`. Its route handler in `platform-owner-web/app/api/platform/auth/login/route.ts` forwards to backend `/platform/auth/login` using `BACKEND_API_URL`, sets an HttpOnly token cookie, and does not expose the token to browser JavaScript. This is correct for the Platform Owner UI.
- **Separation of roles:** the current Next.js page is specifically the Platform Owner interface; it is not the tenant Admin login page. Do not change the Platform Owner login endpoint to `/api/v1/auth/login`. If a tenant Admin login UI is added later, it must use the tenant-user login route and keep its session/authentication separate from the Platform Owner cookie.
- **Tenant list/create proxies:** the Next.js `/api/platform/tenants` route forwards authenticated requests to backend `/platform/tenants` with the Platform Owner bearer token. The dashboard lists tenant and Admin summary information, and the create-company flow displays the Admin activation URL.
- **Database safety:** two existing tenants/Admin accounts were visible in the UI. Do not rerun bootstrap against the existing database, reset the database, or edit applied Flyway migrations.
- **Verification evidence:** user confirmed that the tenant Admin login worked after using the correct endpoint. Earlier user output showed the API container/build succeeded and the unauthenticated tenant-list endpoint correctly returned 401. Frontend source was reviewed on GitHub; this note does not claim a new automated UI test was run during this turn.
- **Next steps:** (1) run a quick UI regression check: Platform Owner login → tenant list → logout; (2) verify tenant Admin login through `POST /api/v1/auth/login`; (3) add/update automated checks for the frontend route mapping if the current test setup supports them; (4) review the frontend README because its note that the tenant list is unavailable is now stale; (5) update progress after observed test results.
- **Git safety:** continue work on the appropriate feature branch. Do not merge into `main` without explicit approval.


## Current workspace

- **Repository:** [contractor-manager](https://github.com/MohammedAli01020/contractor-manager)
- **Backend working branch:** `feature/platform-owner-activation`
- **Platform Owner web UI branch:** `feature/platform-owner-web`
- **Local project path:** `D:\\contractor-manager`
- **Main branch:** Do not merge into `main` without explicit approval.

## Current status

**Current status:** Backend bootstrap work and the separate Next.js Platform Owner UI are both in progress on their respective feature branches. The login-routing issue for tenant Admin `mali` was resolved by using `POST /api/v1/auth/login`; the Platform Owner UI correctly remains on `POST /api/v1/platform/auth/login`. Continue with local UI regression checks, route-mapping tests, and the remaining backend bootstrap security review.

### Confirmed completed

- Platform Owner feature implementation exists on the working branch.
- Platform Owner authentication and tenant-admin activation behavior have been reviewed in the security configuration, JWT filter, service, and integration-test code.
- Full backend suite passed in the user's local run on 2026-10-25 at **23:58:11 (+03:00)**: `mvn test`, **95 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. This is user-provided output, not a GitHub Actions result.
- On 2026-10-25, the user ran `mvn "-Dtest=PlatformOwnerActivationIntegrationTest" test` from `D:\\contractor-manager\\backend`.
- **Reported focused result:** 4 tests, 0 failures, 0 errors, 0 skipped; **BUILD SUCCESS**; total time about 2 minutes 10 seconds. This is user-provided test evidence, not independently run by the assistant.
- The focused activation test class covers disabled Platform Owner access using an already-issued JWT, refresh rejection for an inactive tenant admin, and concurrent activation attempts consuming the same token. The class reports 4 tests total.
- GitHub Actions workflow `.github/workflows/backend-ci.yml` was added to this feature branch in commit `e1558450d390ba9eb5d25ba8857529e0810deed4`. It runs backend Maven tests on pushes/PRs to this feature branch or via manual dispatch, writes a test summary, and uploads Surefire reports for 14 days. It has read-only repository permissions and does not auto-commit or update this progress file.
- A standalone Platform Owner bootstrap CLI was added in commit `b624a7af27ea3e2407c86e683c0ee8721d218f2c`. It starts Spring with `WebApplicationType.NONE`, enables the existing bootstrap runner for the command, prints a generic result, closes the context, and exits nonzero on failure. It reuses the existing bootstrap service/runner rather than duplicating account-creation logic.
- A focused test for the CLI was added in commit `8a6ed9937fea4ffa3f1ca6fec39dbb74bbe97fff`. **Backend CI run #4 failed** because the test called unsupported `SpringApplication.getDefaultProperties()`. The test was corrected in commit `f62e88e274919b4d3757b0e11c9ae4b3459f33a6` by removing that unsupported assertion while retaining the assertion that the CLI uses `WebApplicationType.NONE`.
- **Latest verified GitHub Actions result:** [Backend CI run #5](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40234503410), on `feature/platform-owner-activation`, commit `f62e88e274919b4d3757b0e11c9ae4b3459f33a6`, completed successfully on 2026-10-26 local time (2026-10-25 UTC). Logs confirm **96 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; Maven total time 01:00 min**. The `PlatformOwnerBootstrapCliTest` itself reports 1 passing test. This is verified CI evidence; it is distinct from the earlier user-reported local run of 95 tests.
- CI history checked: run #1 succeeded; run #2 succeeded; run #3 was cancelled; run #4 failed on the CLI test compilation; run #5 succeeded after the test fix. Keep this failure-and-repair history rather than recording only successful outcomes.

### Security-test status and remaining gaps

The following three activation/security checks were covered by the focused integration-test class and passed in the user's reported local run on 2026-10-25 (4 tests, no failures/errors). Do not rerun this focused class unnecessarily unless code affecting these paths changes or a failure requires diagnosis.

1. **Passed (focused test):** a Platform Owner disabled after obtaining a JWT is denied on subsequent protected requests.
2. **Passed (focused test):** a tenant admin cannot use refresh-token flow before account activation.
3. **Passed (focused test):** concurrent attempts to consume the same activation token cannot both succeed.
4. **Still to consider:** a concurrent bootstrap test to complement the existing sequential single-use bootstrap test.
5. **Still to review:** bootstrap credential validation and environment-variable handling, prevention of unintended owner creation during ordinary API startup, password handling/logging, failure exit behavior, and remaining authorization/validation edge cases.

These are test-coverage and review items, not claims of confirmed implementation defects. The passing CLI test verifies non-web mode selection; it does not by itself prove every end-to-end bootstrap scenario listed above.

## Agreed bootstrap direction

- Preferred target: a dedicated administrative CLI command to create the initial Platform Owner, rather than using normal API server startup as the creation trigger.
- Existing implementation: `PlatformOwnerBootstrapRunner` uses Spring Boot `ApplicationRunner`, gated by `app.platform.bootstrap.enabled=true`, and delegates to `PlatformBootstrapService`.
- The CLI entry point now exists and configures `WebApplicationType.NONE`, enables the bootstrap runner for that command, closes the context, and exits with a nonzero status on failure.
- Reuse the existing service and database guard; do not duplicate creation logic.
- Do not remove the existing runner until startup safety is reviewed and the CLI workflow has been verified end to end.
- Do not put passwords in source control, command-line arguments, or logs; use environment-based configuration and verify the actual implementation during the security review.

## Agreed execution plan — next steps

Follow this order. Continue on `feature/platform-owner-activation`; do not merge to `main` without explicit approval.

1. **Review bootstrap security.** Inspect the CLI, runner, service, and configuration. Verify environment-variable names/requirements, invalid or missing credentials, password hashing and logging, prevention of owner creation during ordinary API startup, generic failure output, nonzero exit on failure, and clean context shutdown. Report findings before making any application-code changes.
2. **Close relevant test gaps.** Add/adjust tests only where review shows they are needed: first creation, repeated invocation, invalid/missing credentials, password hashing, concurrent attempts, nonzero exit on failure, clean process exit, and normal API startup without owner creation. A concurrent bootstrap test remains a possible follow-up. Do not repeat already-passing tests without a relevant code change or diagnostic need.
3. **Run and record verification.** After any approved code changes, run targeted tests and then `mvn test`; verify the resulting GitHub Actions run. Record only observed results and distinguish local output from CI evidence.
4. **Run the local backend and MySQL.** Use the repository's documented setup. Create the first Platform Owner through the CLI, then verify login at `POST /api/v1/platform/auth/login` and the protected tenant-creation endpoint. Use local/test credentials only.
5. **Build the Platform Owner web UI with Next.js.** This is separate from the existing Arabic tenant-admin activation page. Start with a login page and protected dashboard shell, then a tenant/company creation screen. Keep UI validation for usability only; authorization and business rules stay in Spring Boot.
6. **Connect Next.js to the Spring Boot API.** Use the local backend base URL during development. Integrate `POST /api/v1/platform/auth/login` and `POST /api/v1/platform/tenants`, handle unauthorized/expired sessions safely, and keep the Platform Owner token separate from tenant-user authentication. Decide the final token storage/session approach before production; do not put secrets in client-side source.
7. **Verify the full workflow end to end.** Platform Owner login → create tenant and first ADMIN → privately share activation link → tenant ADMIN sets a password → tenant ADMIN can log in. Also test tenant isolation, Platform Owner disabled/revoked access, expired/replayed activation links, and failure/retry behavior.
8. **Deploy only after local verification.** Decide the production hosting arrangement for Next.js and Spring Boot, configure HTTPS and environment secrets, and test the deployed activation URL. Production hosting decisions can wait until local verification is complete.
9. **Review the diff and Git state.** Keep commits on `feature/platform-owner-activation`; do not merge to `main` without explicit approval.

## Working rules

- Focus on the **backend** unless the user asks otherwise; do not start Flutter work by assumption.
- Work in small, explained steps and provide copy-paste Windows CMD commands when useful.
- Never claim a test passed unless its output confirms it.
- Do not edit previously-applied Flyway migrations.
- Before resuming in a new chat, read this file, then verify the actual branch, commit, working tree, and relevant test output. This note is a handoff aid, not a substitute for checking the repository.


## Platform Owner UI increment — company details view

- Added a read-only company details view on feature/platform-owner-web. Open it from the “عرض التفاصيل” link beside a company name in the dashboard.
- The view displays tenant ID/name/status/creation date and the Admin summary (name, username, user ID, account active state, and password-activation state) from the already-loaded tenant list.
- This is intentionally read-only: it does not invent backend endpoints or attempt to suspend/reactivate a company or change an Admin account. Those actions need a backend capability/security review first.
- Added responsive styling for the details view.
- Commits: 22a9b964de0df01bd0d31267a33f7fc0c12ece68 (view), 66f53243331dbd79e778bb7f756a6dcb6ca38312 (styles).
- Verification pending: this change has not yet been run through local lint/build or manual browser testing. Do not report it as verified until the user runs those checks.
- Next local check: inspect git status -sb before pulling; if the working tree is clean, pull feature/platform-owner-web, then run npm run lint and npm run build from platform-owner-web. Keep existing DB data intact and do not merge into main without explicit approval.


## Request tracking increment — 2026-10-28 (verification pending)

- **Branch:** `feature/request-tracking`, based on `feature/platform-owner-totp-2fa`. No merge to `main`, no deployment, and no database or Flyway migration changes.
- Added `RequestTrackingFilter` to assign a request ID to each HTTP request, preserve a supplied `X-Request-ID` only when it matches the safe 1–64 character allowlist, return the ID in the response header, put it in SLF4J MDC during request processing, and remove the MDC value in `finally`.
- Added structured completion/failure log messages with request ID, HTTP method, URI path, response status, duration, and exception class. Query strings, headers, bodies, credentials, and tokens are not logged by this filter.
- Added `requestId` to the standard `ApiError` response body and to security 401/403 JSON responses written before controller handling.
- Added unit tests for safe ID preservation, generated UUIDs for missing/invalid IDs, response header behavior, and MDC cleanup. Added integration tests asserting that both a failed login and an unauthenticated protected request include the request ID in the response header and error JSON.
- **Verification pending:** changes were committed through the GitHub file API, but tests/build and CI have not yet been observed. Do not describe this increment as verified until the checks pass.
- **Next:** inspect the complete diff and run backend tests/CI on this branch. Then address any findings before moving to login throttling/security events. Idempotency and offline sync remain future phases; this change does not implement either.
- **Safety:** existing database and migrations were not changed. Do not merge to `main` or deploy without explicit approval.

## Login throttling follow-up review — 2026-10-28

- **Branch:** `feature/request-tracking`.
- **CI evidence:** GitHub Actions [Backend CI run 40666959386](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40666959386) completed successfully on code commit `e7f7541af4f66b07d37db4d9b73103290896e688`. The current branch HEAD is `8ee12ad77d8c66f956f098e0f76dc68890d52aa6`; the only commit after the green run is the documentation update `Document limiter hardening and expanded test coverage`. The code changes in this review were included in the tested commit.
- **Review checked:** both tenant and Platform Owner login controller integrations; in-memory limiter thresholds and expiry; safe security-event logging; platform dummy BCrypt check; tests for expiry, per-IP threshold, concurrent failure recording, and both login endpoints.
- **Remaining production caveat:** the 50,000-bucket ceiling rejects requests for new identities when the map is full. This bounds memory, but an attacker able to fill the map could cause a broad login-availability failure for otherwise legitimate new identities. Treat this as a known trade-off to resolve before production; consider a bounded cache/eviction strategy or shared rate-limit store with explicit abuse and availability behavior.
- **Concurrency caveat:** the unit test verifies concurrent failure recording, not many simultaneous in-flight login requests. Since `checkAllowed` and `recordFailure` are separate operations, concurrent requests can pass the check before earlier failures are recorded. Add a focused concurrency test and decide whether an in-flight attempt guard is warranted.
- **Deployment caveat:** counters remain process-local and reset on restart. A shared atomic store is required for consistent enforcement across multiple backend instances. The client IP uses `getRemoteAddr()`; behind a reverse proxy, configure trusted-proxy handling rather than trusting arbitrary forwarded headers.
- **Database safety:** no schema/Flyway changes were introduced. No merge to `main` and no production deployment.



## Login throttling hardening — CI verified (2026-10-28)

- **Branch:** `feature/request-tracking`.
- **User-provided GitHub Actions result:** Maven test step **success** for code/test commit `cb7c18f4968a37f44174bb5ddfb433724b456527`. This confirms the Maven test step passed for that commit; it does not imply a separate production or deployment verification.
- **Current branch HEAD at handoff:** `6cfa193e1b40ba948665940e818a2cfc7110edbf` (documentation follow-up after the tested code/test commit).
- **Implemented:** separate bounded identity/IP bucket maps (50,000 identity buckets; 10,000 IP buckets), periodic expired-entry pruning, oldest-entry eviction at capacity, fixed lock stripes, and serialized tenant and Platform Owner login attempts for the same normalized username/IP identity. Added tests for capacity behavior, positive capacity validation, and stable identity lock selection.
- **Remaining concurrency caveat:** requests for different usernames from the same IP are not serialized around the shared IP-wide threshold; simultaneous requests may race at that threshold. Do not claim all concurrent limiter races are eliminated.
- **Production caveats:** counters remain per-process and reset on restart; multiple replicas need a shared atomic rate-limit store. Behind a reverse proxy, configure trusted client-IP handling. Bounded eviction can allow high-cardinality traffic to evict older counters, so abuse/availability behavior still needs production review.
- **Safety:** no database schema/Flyway changes, deployment, or merge to `main`.


## Login throttling IP-wide concurrency follow-up — 2026-10-28 (CI pending)

- **Branch:** `feature/request-tracking`.
- **Finding addressed:** previously, different usernames from the same client IP could concurrently pass the IP-wide failure check before any of those requests recorded their failures.
- **Change:** added a second fixed 256-stripe lock set for client IPs and wrapped both tenant and Platform Owner login flows with the IP lock first, then the existing identity lock. This serializes the full login/authentication attempt for requests mapped to the same IP stripe and keeps lock allocation bounded.
- **Test added:** confirms the IP lock is stable for the same IP and distinct for the two selected test IPs. This is a lock-selection unit test; it is not a full concurrent HTTP/controller integration test.
- **Trade-off:** requests from the same IP are serialized while password verification runs; users sharing NAT/proxy IPs may see increased login latency. Evaluate performance and consider a shared atomic reservation/rate-limit design before production.
- **CI:** GitHub Actions run [40673134508](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40673134508) was pending when this note was written. Do not claim this increment passes until its Maven test step completes successfully.
- **Other production caveats remain:** the limiter is process-local and resets on restart; multi-replica deployments need a shared atomic store; proxy client-IP handling must be configured safely; bounded bucket eviction can let high-cardinality abuse evict older counters.
- **Safety:** no database/schema/Flyway changes, no deployment, and no merge to `main`.


## IP-wide login concurrency fix — latest handoff (2026-10-28)

- **Branch:** `feature/request-tracking`; latest code commit before this documentation entry: `204332a1cb20d5a0424d8a5588f59255de36536b`.
- **Change:** tenant and Platform Owner login flows now acquire a fixed-size IP lock stripe before the normalized identity lock stripe. This serializes login requests sharing an IP stripe, preventing concurrent failures for different usernames from racing past the shared IP-wide threshold.
- **Test:** added a unit test for stable IP-lock selection. It does not replace a concurrent controller/integration test.
- **Trade-off:** same-IP login flows are serialized while authentication/password verification runs; users behind shared NAT/proxies may experience increased latency. Evaluate this before production and consider a shared atomic rate-limit/reservation design.
- **CI status:** earlier runs triggered by intermediate commits were cancelled as later commits were pushed. The final source/documentation state should be verified by the latest Backend CI run; do not infer a pass from the earlier success on commit `cb7c18f4968a37f44174bb5ddfb433724b456527`.
- **Remaining production caveats:** in-memory counters reset on restart and are not shared across replicas; configure trusted proxy client-IP handling; bounded eviction can let high-cardinality abuse evict older counters.
- **Safety:** no database/schema/Flyway changes, no deployment, and no merge to `main`.
