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

The create-tenant response includes an activation URL for manual sharing. Share it with the intended tenant administrator over a trusted channel. The token is generated using secure randomness and only its SHA-256 hash is stored. It expires after `app.platform.activation-hours` (default 24 hours) and cannot be replayed after successful activation.

## Free static activation page

The static page is in `activation-page/` and can be hosted on Cloudflare Pages' free static hosting plan. It does not require a separate backend, paid domain, email, or SMS service.

1. In `activation-page/config.js`, set `API_BASE_URL` to the public HTTPS Railway backend origin (without a trailing slash).
2. Deploy the `activation-page` directory to Cloudflare Pages. The page's origin will be a `pages.dev` address unless you configure a custom domain.
3. Set the Railway environment/property `app.platform.activation-base-url` to the public page URL, for example `https://your-project.pages.dev/index.html`.
4. Configure backend CORS to allow only the exact deployed activation-page origin. Do not use wildcard origins in production when credentials or authenticated endpoints are involved. The page uses no cookies or credentials.
5. Verify that Railway's public HTTPS URL is reachable and that the activation endpoint is exposed at `/api/v1/platform/admin-activation`.

The page validates that the two password fields match and submits the token and new password to the activation API. It removes the token from the visible address bar as soon as it loads. The token is still a bearer secret, so only share the URL with the intended administrator. A free static host does not guarantee that the Railway backend or database plan is free.

## Security and deployment notes

- Use HTTPS for activation links outside local development.
- Do not log or persist the raw activation token outside the one-time creation response.
- Keep the owner bootstrap flag disabled during normal operation.
- Existing users remain activated through the V10 migration default. Tenant user login and refresh reject accounts whose `password_activated` flag is false. Tenant registration remains disabled outside the test profile.
- Run the full Maven integration suite against MySQL/Testcontainers before merging this feature.
