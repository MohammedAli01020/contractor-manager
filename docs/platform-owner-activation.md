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
- **Still pending:** run `mvn test` again after the test additions to confirm the complete backend suite. Do not rerun the focused class without a relevant code change or failure to investigate.

## Initial Platform Owner bootstrap

### Current implementation (existing; not the target workflow)
The current branch contains `PlatformOwnerBootstrapRunner`, a Spring Boot `ApplicationRunner` enabled only when `app.platform.bootstrap.enabled=true`. It reads `PLATFORM_OWNER_USERNAME`, `PLATFORM_OWNER_FULL_NAME`, and `PLATFORM_OWNER_PASSWORD`, then calls `PlatformBootstrapService.createInitialOwner(...)`. The service validates input, hashes the password, locks the database-backed singleton guard, and rejects creation if an owner already exists.

Because this is an application startup runner, leaving the flag enabled can cause a later application startup to fail once an owner already exists. Do not leave the flag enabled for normal operation. This is existing behavior; it is not a CLI implementation.

### Agreed target: a dedicated administrative CLI command
The preferred long-term operator workflow is a separate command for creating the initial Platform Owner, which runs the bootstrap task and exits without starting the public API web server.

- Reuse `PlatformBootstrapService`; do not duplicate validation, hashing, transaction, or guard-locking logic.
- Do not merely swap `ApplicationRunner` for `CommandLineRunner`: both execute during Spring Boot startup and do not alone create an independent CLI workflow.
- Do not put passwords in source control, command-line arguments, or logs. Prefer a secure interactive prompt or an appropriate deployment secret source.
- Reject repeated creation when an owner already exists; keep the database guard and transaction protection.
- Return a clear success/failure result and a non-zero process exit on failure.
- Test concurrent attempts, invalid/missing credentials, password hashing, repeated invocation, clean process exit, and normal API startup with bootstrap disabled.
- Keep the existing Runner until the CLI path is implemented and tested. Then decide whether to remove it or restrict it to explicitly controlled local/development use. Avoid undocumented duplicate production paths.
- Keep all work on `feature/platform-owner-activation`; do not merge into `main` without explicit approval.

**Decision status:** The CLI direction is agreed. The CLI implementation and its tests are **not yet implemented or verified**.
## Platform API

- `POST /api/v1/platform/auth/login` — owner login; returns a short-lived access token, without a refresh token.
- `POST /api/v1/platform/tenants` — requires a Platform Owner token. Creates the tenant and its first ADMIN account, with password activation disabled.
- `POST /api/v1/platform/admin-activation` — public one-time activation endpoint; body contains `token` and a new `password`.

The create-tenant response includes an activation URL for manual sharing. The token is generated using secure randomness and only its SHA-256 hash is stored. It expires after `app.platform.activation-hours` (default 24 hours) and cannot be replayed after successful activation.

## Local development and Railway deployment

### Local development and agreed next steps

Follow this sequence rather than starting the frontend first:

1. **Security verification status:** the focused tests for Platform Owner disabled after JWT issuance, tenant-admin refresh before activation, and concurrent activation-token consumption have passed in the user's reported run (4 tests, no failures/errors, 2026-10-25). Do not rerun those same focused tests without a relevant code change. Run the full `mvn test` suite next and record its actual result. A concurrent bootstrap test remains a possible additional test.
2. **Implement the standalone bootstrap CLI.** It should create the first Platform Owner and exit without starting the public API web server. Reuse `PlatformBootstrapService` for validation, password hashing, transactionality, the database guard lock, and the one-owner check. Keep credentials out of source control, command-line arguments, and logs. Retain the current startup Runner until the CLI and its tests pass, then decide whether to remove it or restrict it to controlled development use.
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
