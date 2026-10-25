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

The create-tenant response includes an activation URL/token for manual sharing. Share it with the intended tenant administrator over a trusted channel. The token is generated using secure randomness and only its SHA-256 hash is stored. It expires after `app.platform.activation-hours` (default 24 hours) and cannot be replayed after successful activation. Configure `app.platform.activation-base-url` to the externally reachable activation page/API URL for the deployment; the default localhost URL is for local development only.

Tenant user login and refresh reject accounts whose `password_activated` flag is false. Existing users remain activated through the V10 migration default. Tenant registration remains disabled outside the test profile.

## Operational cautions

- Use HTTPS for activation links outside local development.
- Do not log or persist the raw activation token outside the one-time creation response.
- Keep the owner bootstrap flag disabled during normal operation.
- Run the full Maven integration suite against MySQL/Testcontainers before merging this feature.
