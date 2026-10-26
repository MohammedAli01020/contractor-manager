# Platform Owner and tenant-admin activation

## Agreed product and architecture decisions

These are the current agreed decisions for this feature:

- Only the **Platform Owner** creates tenant companies and their first `ADMIN` account. A tenant `ADMIN` cannot create companies or access another tenant.
- The first tenant `ADMIN` chooses their own password through a temporary activation link. Do not issue or send a temporary password.
- The activation token is generated with secure randomness; store only its SHA-256 hash, expire it after 24 hours by default, and allow successful activation once only.
- The activation page stays inside the Spring Boot application for now, using static HTML/CSS/JavaScript. This avoids a separate frontend host and same-origin/CORS setup for this small, one-purpose page.
- The full Platform Owner dashboard will be a **Next.js** web frontend later. It will call the existing Spring Boot REST API; it must not duplicate business rules or authorization in the frontend.
- Develop and test locally on the laptop first without paying for a separate frontend host. Decide the production hosting layout when deployment is ready. The activation page can be served from the same Spring Boot service as the API.
- Do not merge this feature into `main` until code review and actual test results have been checked.

## Current implementation progress (branch: `feature/platform-owner-activation`)

The following implementation files/features are present on this branch, but presence of code is not proof that the feature compiles or passes tests:

- Platform Owner bootstrap/authentication and a separate Platform Owner JWT type/authorization path.
- Platform Owner API to create a tenant and its first `ADMIN` user.
- Tenant-admin activation token storage, token hashing/expiry/single-use flow, and rejection of tenant login/refresh before activation.
- Flyway migrations for the Platform Owner and activation changes (V10/V11, as currently documented in this branch).
- Arabic RTL activation page in `backend/src/main/resources/static/activate-admin/`:
  - `index.html`
  - `styles.css`
  - `app.js`
- Integration test source: `PlatformOwnerActivationIntegrationTest.java`.
- Setup/security notes and this progress record.

**Recorded backend test evidence:**
- Earlier full-suite run on 2026-10-25: `mvn test` completed with **92 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** (from `D:\\contractor-manager\\backend`). This predates the focused security-test additions.
- Focused run reported by the user on 2026-10-25: `mvn "-Dtest=PlatformOwnerActivationIntegrationTest" test` completed with **4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** in about 2 minutes 10 seconds.
- The focused run covers the disabled-owner/old-JWT case, inactive tenant-admin refresh rejection, and concurrent activation-token consumption. These focused checks are recorded as passed based on the user's actual output.
- **Latest CI status:** the full backend Maven test suite passed on commit `8705c2444c19ea70d0e5f03b54482c0355679ee8` (GitHub Actions run #10). A new run is in progress for the current documentation-only commit. Do not describe a successful CI run as proof of the separate fresh-database CLI runtime workflow.

## Initial Platform Owner bootstrap

### Implemented CLI and underlying bootstrap runner
The branch contains `PlatformOwnerBootstrapCli`, a dedicated Spring Boot entry point configured with `WebApplicationType.NONE`. It enables `app.platform.bootstrap.enabled=true`, so the existing `PlatformOwnerBootstrapRunner` reads `PLATFORM_OWNER_USERNAME`, `PLATFORM_OWNER_FULL_NAME`, and `PLATFORM_OWNER_PASSWORD` and calls `PlatformBootstrapService.createInitialOwner(...)`. The service validates input, hashes the password, locks the database-backed singleton guard, and rejects creation if an owner already exists.

The CLI closes its Spring context after the runner completes and exits with a non-zero code when startup/bootstrap fails. `SecurityConfig` keeps `PasswordEncoder` available in non-web mode and only creates the HTTP `SecurityFilterChain` for servlet web applications.

The older `ApplicationRunner` is the implementation mechanism used inside the dedicated CLI process; it is not a replacement for the CLI entry point. Do not leave `app.platform.bootstrap.enabled=true` on normal API startup, because the runner is single-use and will reject creation after an owner exists.

- Reuse `PlatformBootstrapService`; do not duplicate validation, hashing, transaction, or guard-locking logic.
- Do not merely swap `ApplicationRunner` for `CommandLineRunner`: both execute during Spring Boot startup and do not alone create an independent CLI workflow.
- Do not put passwords in source control, command-line arguments, or logs. Prefer a secure interactive prompt or an appropriate deployment secret source.
- Reject repeated creation when an owner already exists; keep the database guard and transaction protection.
- Return a clear success/failure result and a non-zero process exit on failure.
- Test concurrent attempts, invalid/missing credentials, password hashing, repeated invocation, clean process exit, and normal API startup with bootstrap disabled.
- Keep the runner conditional on `app.platform.bootstrap.enabled=true`; the dedicated CLI enables it for its own process. Normal API startup must leave this property disabled.
- Keep all work on `feature/platform-owner-activation`; do not merge into `main` without explicit approval.

**Verification status:** The focused CLI unit test asserts that `WebApplicationType.NONE` is used, and the full Maven test suite passed in GitHub Actions on commit `8705c2444c19ea70d0e5f03b54482c0355679ee8`. A fresh-database end-to-end bootstrap run is still pending and must use an isolated disposable test database, never the existing database that already has a Platform Owner.
## Platform API

- `POST /api/v1/platform/auth/login` — owner login; returns a short-lived access token, without a refresh token.
- `POST /api/v1/platform/tenants` — requires a Platform Owner token. Creates the tenant and its first ADMIN account, with password activation disabled.
- `POST /api/v1/platform/admin-activation` — public one-time activation endpoint; body contains `token` and a new `password`.

The create-tenant response includes an activation URL for manual sharing. The token is generated using secure randomness and only its SHA-256 hash is stored. It expires after `app.platform.activation-hours` (default 24 hours) and cannot be replayed after successful activation.

## Local development and Railway deployment

### Local development and agreed next steps

Follow this sequence rather than starting the frontend first:

1. **Security verification status:** the latest full Maven test run passed: 100 tests, 0 failures, 0 errors, and 0 skipped. Earlier focused security tests also passed in the reported run (4 tests, 2026-10-25).
2. **Standalone bootstrap CLI:** the CLI uses `WebApplicationType.NONE` and enables `app.platform.bootstrap.enabled=true`. `SecurityConfig` keeps the password encoder available in non-web mode and limits the HTTP security filter chain to servlet applications. Actual bootstrap execution against an isolated test database remains unverified. Before real use, verify successful creation, invalid credentials, repeated invocation, clean process exit, and normal API startup with bootstrap disabled. Never run it against the existing database, and keep credentials out of source control, command-line arguments, and logs.
3. **Start Spring Boot and MySQL locally** using the repository's documented setup. Create the initial Platform Owner with the CLI and verify owner login at `POST /api/v1/platform/auth/login`.
4. **Build a separate Next.js Platform Owner frontend** with a login page, protected dashboard shell, and tenant-creation screen. This is not the existing tenant-admin activation page; that page remains static HTML/CSS/JavaScript served by Spring Boot.
5. **Connect Next.js to the local Spring Boot API.** Use `POST /api/v1/platform/auth/login` and `POST /api/v1/platform/tenants`. Keep Platform Owner authentication separate from tenant-user authentication. Decide the production-safe session/token storage approach before deployment; never commit credentials or secrets in frontend source.
6. **Test the complete workflow:** Platform Owner login → create tenant and first ADMIN → privately share activation URL → first ADMIN chooses a password on `/activate-admin/index.html` → ADMIN can log in. Verify tenant isolation, disabled-owner access, activation expiry/replay, and failure/retry cases.
7. **Deploy after local verification.** Choose the final Next.js/Spring Boot hosting arrangement later, configure HTTPS and environment secrets, and test the real public activation URL. No separate frontend hosting is required during local development.

Use local/test credentials only. Do not merge this feature into `main` without explicit approval.

### Serve the activation page from the same Spring Boot service in production

The activation page is served by the same Spring Boot application as the API, so the browser calls the API on the same origin and no separate static hosting or CORS configuration is required for this page.

1. The page assets live in `backend/src/main/resources/static/activate-admin/`.
2. Set the deployed environment property `APP_PLATFORM_ACTIVATION_BASE_URL` (Spring relaxed-binding equivalent of `app.platform.activation-base-url`) to the public HTTPS URL plus `/activate-admin/index.html`, for example `https://your-service.up.railway.app/activate-admin/index.html`. Do not include a token in this setting.
3. Keep the page and API on the same public Spring Boot service.
4. Create a tenant using the Platform Owner API, then share the returned activation URL privately with the intended administrator.
5. Test successful activation, expired tokens, and replay attempts in a non-production environment before relying on the workflow.

The URL must use the real public HTTPS domain assigned to your service. Do not copy the example domain literally. Any hosting/runtime charges depend on the hosting plan and usage.

## Security and deployment notes

- Use HTTPS for activation links outside local development.
- The raw activation token is a bearer secret. It should be shared only with the intended administrator and must not be logged.
- The page removes the token from the visible address bar after reading it. The token remains in memory only for the activation request.
- Keep the owner bootstrap flag disabled during normal operation.
- Existing users remain activated through the V10 migration default. Tenant user login and refresh reject accounts whose `password_activated` flag is false. Tenant registration remains disabled outside the test profile.
- The frontend performs basic usability validation only. Spring Boot must validate the token, enforce expiry/single use, hash the password using the configured password encoder, and control account activation.
- Before merge, run the full Maven integration suite against MySQL/Testcontainers and verify at least: owner bootstrap/authentication, tenant creation, login blocked before activation, successful activation, expired token, token replay, tenant isolation, and access-control boundaries.

## Progress update - 2026-10-26

- Dedicated bootstrap CLI and its test source are present.
- Full Maven test suite passed: 100 tests, 0 failures, 0 errors, 0 skipped.
- `git diff --check` reported no whitespace errors in the last check.
- Actual bootstrap execution against a fresh database is not verified.
- Commit and push are pending final review.
- Next phase: build the separate Next.js Platform Owner frontend after backend review and push.
