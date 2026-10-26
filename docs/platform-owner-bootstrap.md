# Initial Platform Owner Bootstrap

## Purpose

Create the first Platform Owner once for a new database. Do not run this during normal API startup.

## Required environment variables

- `PLATFORM_OWNER_USERNAME`
- `PLATFORM_OWNER_FULL_NAME`
- `PLATFORM_OWNER_PASSWORD`
- `DB_USERNAME` and `DB_PASSWORD`
- Any other secrets required by the application configuration

The current CLI reads the bootstrap password from an environment variable; it does not prompt interactively. Never store real credentials in source control, command-line arguments, or logs.

## Implementation

- CLI: `backend/src/main/java/com/contractor/app/platform/infra/PlatformOwnerBootstrapCli.java`
- Runner: `backend/src/main/java/com/contractor/app/platform/infra/PlatformOwnerBootstrapRunner.java`
- The CLI enables `app.platform.bootstrap.enabled=true`, uses `WebApplicationType.NONE` so it does not start an embedded web server.
- It closes the Spring application context after the bootstrap attempt.
- The bootstrap service rejects creation if a Platform Owner already exists.

## One-time operation and security

1. Start the database and confirm application database connectivity.
2. Set the required environment variables securely for the current process.
3. From the `backend` directory, run the Maven command shown below.
4. Confirm the success message: `Initial Platform Owner bootstrap completed.`
5. Remove bootstrap credentials from the environment when no longer needed.

Do not enable `app.platform.bootstrap.enabled=true` during normal API startup. Do not reset or delete database data just to recreate an owner. Run the one-time CLI from the `backend` directory with: `mvn "org.codehaus.mojo:exec-maven-plugin:3.5.0:java" "-Dexec.mainClass=com.contractor.app.platform.infra.PlatformOwnerBootstrapCli" "-Dexec.classpathScope=runtime"`. This command syntax was checked with execution skipped; the actual bootstrap invocation has not yet been tested.

## Verify login

Endpoint: `POST http://localhost:8081/api/v1/platform/auth/login`

Request body example (use placeholders only in documentation):

```json
{
  "username": "YOUR_PLATFORM_OWNER_USERNAME",
  "password": "YOUR_PLATFORM_OWNER_PASSWORD"
}
```

Use the actual local API port if different. A successful response includes a short-lived bearer access token. Keep that token secret and do not print it in shared logs.

Keep work on the feature branch until tests and review are complete. Do not commit, push, or merge without approval.
