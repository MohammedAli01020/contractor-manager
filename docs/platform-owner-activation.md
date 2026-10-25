# Platform Owner and tenant-admin activation

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

## Host the activation page on Railway (same Spring Boot service)

The activation page is served by the same Spring Boot application as the API, so the browser calls the API on the same origin and no separate static hosting or CORS configuration is required.

1. The page assets live in `backend/src/main/resources/static/activate-admin/`.
2. Set Railway's `app.platform.activation-base-url` property to the public URL of the service plus `/activate-admin/index.html`, for example `https://your-service.up.railway.app/activate-admin/index.html`. Do not include a token in this setting.
3. Keep the API and page on the same public Railway service. The page submits to the relative path `/api/v1/platform/admin-activation`.
4. Create a tenant using the Platform Owner API, then share the returned activation URL privately with the intended administrator.
5. Test successful activation, expired tokens, and replay attempts in a non-production environment before relying on the workflow.

The URL must use the real public HTTPS domain assigned to your Railway service. Do not copy the example domain literally. Railway hosting uses the existing service; any hosting/runtime charges depend on your Railway plan and usage.

## Security and deployment notes

- Use HTTPS for activation links outside local development.
- The raw activation token is a bearer secret. It should be shared only with the intended administrator and must not be logged.
- The page removes the token from the visible address bar after reading it. The token remains in memory only for the activation request.
- Keep the owner bootstrap flag disabled during normal operation.
- Existing users remain activated through the V10 migration default. Tenant user login and refresh reject accounts whose `password_activated` flag is false. Tenant registration remains disabled outside the test profile.
- Run the full Maven integration suite against MySQL/Testcontainers before merging this feature.
