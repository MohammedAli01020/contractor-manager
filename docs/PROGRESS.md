# Project Progress

> This file is the handoff note for continuing work across ChatGPT conversations. Update it only after checking the actual repository state and recording verified results.

## Current workspace

- **Repository:** [contractor-manager](https://github.com/MohammedAli01020/contractor-manager)
- **Working branch:** `feature/platform-owner-activation`
- **Local project path:** `D:\\contractor-manager`
- **Last confirmed commit before this progress file:** `ec03cbf` — `fix: standardize platform activation error responses`
- **Main branch:** Do not merge into `main` without explicit approval.

## Current status

**In progress:** Targeted security verification and the full backend test suite have passed in user-reported local runs. GitHub Actions CI has been added on this feature branch; its first run still needs to be checked before recording a CI result.

### Confirmed completed

- Platform Owner feature implementation exists on the working branch.
- Platform Owner authentication and tenant-admin activation behavior have been reviewed in the security configuration, JWT filter, service, and integration-test code.
- Full backend suite passed in the user's local run on 2026-10-25 at **23:58:11 (+03:00)**: `mvn test`, **95 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. This is user-provided output, not a GitHub Actions result.
- On 2026-10-25, the user ran the focused command `mvn "-Dtest=PlatformOwnerActivationIntegrationTest" test` from `D:\\contractor-manager\\backend`.
- **Reported focused result:** 4 tests, 0 failures, 0 errors, 0 skipped; **BUILD SUCCESS**; total time about 2 minutes 10 seconds. This is user-provided test evidence, not independently run by the assistant.
- The focused test class now covers disabled Platform Owner access using an already-issued JWT, refresh rejection for an inactive tenant admin, and concurrent activation attempts consuming the same token. The class reports 4 tests total.
- The latest confirmed application-code commit before the new security-test commit was `ec03cbf`, which standardizes error responses for `ResponseStatusException` and updates activation integration-test expectations. The additional test commit is `c9f69aab983b245537d28bb1fe0136ec892bcf88`.
- **GitHub Actions:** `.github/workflows/backend-ci.yml` was added to `feature/platform-owner-activation` in commit `e1558450d390ba9eb5d25ba8857529e0810deed4`. It runs backend Maven tests on pushes/PRs to this feature branch or via manual dispatch, writes a test summary, and uploads Surefire reports for 14 days. It has read-only repository permissions and does not auto-commit or update this progress file. **The workflow run result has not yet been verified; do not report CI as passing until the Actions run is inspected.**

### Security-test status and remaining gaps

The first three security checks below are now covered by the focused integration-test class and passed in the user's reported local run on 2026-10-25 (4 tests, no failures/errors). Do not rerun this focused class unnecessarily unless code affecting these paths changes or a failure requires diagnosis.

1. **Passed (focused test):** a Platform Owner disabled after obtaining a JWT is denied on subsequent protected requests.
2. **Passed (focused test):** a tenant admin cannot use refresh-token flow before account activation.
3. **Passed (focused test):** concurrent attempts to consume the same activation token cannot both succeed.
4. **Still to consider:** a concurrent bootstrap test to complement the existing sequential single-use bootstrap test.
5. **Still to do:** inspect the first `Backend CI` run on GitHub Actions and record its actual result; review remaining authorization and validation edge cases as relevant.

These are test-coverage tracking items, not claims of confirmed implementation defects.

## Agreed bootstrap direction

- Preferred target: a dedicated administrative CLI command to create the initial Platform Owner, rather than using normal API server startup as the creation trigger.
- Existing implementation: `PlatformOwnerBootstrapRunner` uses Spring Boot `ApplicationRunner`, gated by `app.platform.bootstrap.enabled=true`, and delegates to `PlatformBootstrapService`.
- Reuse the existing service and database guard; do not duplicate the creation logic.
- A `CommandLineRunner` alone is not a separate CLI because it also runs during application startup. The intended CLI should run the task and exit without starting the public API web server.
- Do not remove the existing runner until the CLI is implemented and tested. CLI implementation remains pending; no implementation or test result is claimed here.

## Agreed execution plan — next steps

Follow this order. The first two steps are backend/security work; the Next.js dashboard comes after the bootstrap workflow is safe.

1. **Security verification — local full suite passed; verify GitHub CI.** The user reported `mvn test` on 2026-10-25 at **23:58:11 (+03:00)**: **95 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. Do not ask them to rerun the same suite without a relevant code change. Next inspect [GitHub Actions](https://github.com/MohammedAli01020/contractor-manager/actions), open `Backend CI`, and confirm the run on `feature/platform-owner-activation`. Record the actual CI result here only after checking it. A concurrent bootstrap test remains a possible follow-up, not a reason to repeat the already-passing focused checks.
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
