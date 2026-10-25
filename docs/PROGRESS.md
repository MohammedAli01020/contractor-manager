# Project Progress

> This file is the handoff note for continuing work across ChatGPT conversations. Update it only after checking the actual repository state and recording verified results.

## Current workspace

- **Repository:** [contractor-manager](https://github.com/MohammedAli01020/contractor-manager)
- **Working branch:** `feature/platform-owner-activation`
- **Local project path:** `D:\\contractor-manager`
- **Last confirmed commit before this progress file:** `ec03cbf` — `fix: standardize platform activation error responses`
- **Main branch:** Do not merge into `main` without explicit approval.

## Current status

**In progress:** Security verification for the Platform Owner and tenant-admin activation flow.

### Confirmed completed

- Platform Owner feature implementation exists on the working branch.
- Platform Owner authentication and tenant-admin activation behavior have been reviewed in the security configuration, JWT filter, service, and integration-test code.
- The backend test suite was last run successfully on 2026-10-25:
  - Command: `mvn test` (from `D:\\contractor-manager\\backend`)
  - Result: **92 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
- The latest confirmed application-code commit is `ec03cbf`, which standardizes error responses for `ResponseStatusException` and updates activation integration-test expectations.
- The activation feature document still needs its verification status updated only after the remaining checks are run and their results are recorded.

### Not yet verified / next security tests

These are gaps in test evidence, **not confirmed implementation defects**:

1. Verify that a Platform Owner disabled after obtaining a JWT is denied on subsequent protected requests.
2. Verify that a tenant admin cannot use refresh-token flow before account activation.
3. Verify that concurrent attempts to consume the same activation token cannot both succeed.
4. Consider a concurrent bootstrap test to complement the existing sequential single-use bootstrap test.
5. Review relevant authorization and validation edge cases and add tests where needed.

## Agreed bootstrap direction

- Preferred target: a dedicated administrative CLI command to create the initial Platform Owner, rather than using normal API server startup as the creation trigger.
- Existing implementation: `PlatformOwnerBootstrapRunner` uses Spring Boot `ApplicationRunner`, gated by `app.platform.bootstrap.enabled=true`, and delegates to `PlatformBootstrapService`.
- Reuse the existing service and database guard; do not duplicate the creation logic.
- A `CommandLineRunner` alone is not a separate CLI because it also runs during application startup. The intended CLI should run the task and exit without starting the public API web server.
- Do not remove the existing runner until the CLI is implemented and tested. CLI implementation remains pending; no implementation or test result is claimed here.

## Agreed execution plan — next steps

Follow this order. The first two steps are backend/security work; the Next.js dashboard comes after the bootstrap workflow is safe.

1. **Refresh repository state and close the security-test gaps.** From Windows CMD:
   ```bat
   cd /d D:\\contractor-manager
   git status -sb
   git log -1 --oneline
   cd /d D:\\contractor-manager\\backend
   mvn "-Dtest=PlatformOwnerActivationIntegrationTest" test
   ```
   Add/adjust focused tests for disabling a Platform Owner after JWT issuance, tenant-admin refresh before activation, and concurrent activation-token consumption. Consider a concurrent bootstrap test. Do not claim new results until the commands have actually run.
2. **Implement a standalone Platform Owner bootstrap CLI.** It must create the initial owner and exit without starting the API web server. Reuse `PlatformBootstrapService`; do not duplicate validation, password hashing, transaction, singleton guard lock, or the one-owner rule. Do not put passwords in source control, command-line arguments, or logs. Keep the current `PlatformOwnerBootstrapRunner` until the CLI is tested; then decide whether to remove it or limit it to controlled development use.
3. **Test the CLI and startup safety.** Cover first creation, repeated invocation, invalid/missing credentials, password hashing, concurrent attempts, a non-zero exit on failure, clean process exit, and normal API startup without owner creation. Run the targeted tests and then `mvn test`; record only observed results.
4. **Run the local backend and MySQL.** Use the repository's documented setup. Create the first Platform Owner through the CLI, then verify login at `POST /api/v1/platform/auth/login` and the protected tenant-creation endpoint. Use local/test credentials only.
5. **Build the Platform Owner web UI with Next.js.** This is a separate frontend from the existing Arabic tenant-admin activation page. Start with a login page and a protected dashboard shell, then a tenant/company creation screen. Keep UI validation for usability only; authorization and business rules stay in Spring Boot.
6. **Connect Next.js to the Spring Boot API.** Use the local backend base URL during development. Integrate `POST /api/v1/platform/auth/login` and `POST /api/v1/platform/tenants`, handle unauthorized/expired sessions safely, and keep the Platform Owner token separate from tenant-user authentication. Decide the final token storage/session approach before production; do not put secrets in client-side source.
7. **Verify the full workflow end to end.** Platform Owner login → create tenant and first ADMIN → privately share activation link → tenant ADMIN sets a password → tenant ADMIN can log in. Also test tenant isolation, Platform Owner disabled/revoked access, expired/replayed activation links, and failure/retry behavior.
8. **Deploy only after local verification.** Decide the production hosting arrangement for Next.js and Spring Boot, configure HTTPS and environment secrets, and test the deployed activation URL. No separate frontend hosting bill is needed while developing locally; production hosting decisions can wait.
9. **Review the diff and Git state.** Commit/push only with explicit approval. Keep work on `feature/platform-owner-activation`; do not merge to `main` without explicit approval.

## Working rules

- Focus on the **backend** unless the user asks otherwise; do not start Flutter work by assumption.
- Work in small, explained steps and provide copy-paste Windows CMD commands when useful.
- Never claim a test passed unless its output confirms it.
- Do not edit previously-applied Flyway migrations.
- Before resuming in a new chat, read this file, then verify the actual branch, commit, working tree, and relevant test output. This note is a handoff aid, not a substitute for checking the repository.
