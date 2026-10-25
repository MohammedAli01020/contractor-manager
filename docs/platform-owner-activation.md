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

**Verification status: NOT VERIFIED.** The Maven/MySQL integration suite has not yet been confirmed as passing for this branch. Review `SecurityConfig` and related wiring against the actual project, run the tests, and fix any compile/security/test failures before deployment or merge. Do not describe this feature as tested until actual results are recorded.

## Initial Platform Owner bootstrap

The bootstrap runner is disabled unless `app.platform.bootstrap.enabled=true`. Run it only during a controlled administrative deployment with the following environment variables set in the server/container environment (do not place the password in shell command arguments or logs):

- `PLATFORM_OWNER_USERNAME`
- `PLATFORM_OWNER_FULL_NAME`
- `PLATFORM_OWNER_PASSWORD` (at least 12 characters)

Start the application once with `--app.platform.bootstrap.enabled=true`. The process will create the initial owner and fail if an owner already exists. The database-backed singleton guard serializes concurrent bootstrap attempts. After successful creation, restart normally without the bootstrap flag and remove the bootstrap secrets from the environment. Never expose this flag as a public HTTP endpoint.

## Platform API

- `POST /api/v1/platform/auth/login` — owner login; returns a short-lived access token, without a refresh token.
- `POST /api/v1/platform/tenants` — requires a Platform Owner token. Creates the tenant and its first ADMIN account, with password activation disabled.
- `POST /api/v1/platform/admin-activation` — public one-time activation endpoint; body contains `token` and a new `password`.

The create-tenant response includes an activation URL for manual sharing. The token is generated using secure randomness and only its SHA-256 hash is stored. It expires after `app.platform.activation-hours` (default 24 hours) and cannot be replayed after successful activation.

## Local development and Railway deployment

### Local development (current recommended next step)

1. Run the backend and MySQL locally using the repository's documented development setup.
2. Open the activation page at `http://localhost:<backend-port>/activate-admin/index.html` after starting Spring Boot. Use the actual port configured for your local run.
3. Develop the full Next.js Platform Owner dashboard as a separate local frontend when that phase begins; point it at the local Spring Boot API.
4. Test owner authentication, tenant creation, activation, and tenant isolation. Never use production owner credentials or production activation links for local tests.

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
