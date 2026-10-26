# Project Progress

> This file is the handoff note for continuing work across ChatGPT conversations. Update it only after checking the actual repository state and recording verified results.

## Current workspace

- **Repository:** [contractor-manager](https://github.com/MohammedAli01020/contractor-manager)
- **Working branch:** `feature/platform-owner-activation`
- **Local project path:** `D:\\contractor-manager`
- **Main branch:** Do not merge into `main` without explicit approval.

## Current status

**In progress:** The standalone Platform Owner bootstrap CLI has been added, its focused CLI-mode test passes in CI, and the latest GitHub Actions backend run succeeded. Next, review bootstrap security and close remaining test-coverage gaps before moving to local end-to-end verification and the web UI.

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

## Handoff update — 2026-10-26 (tenant Admin login diagnosis + local Docker state)

- **Current branch for the login investigation:** `feature/platform-owner-activation`. The tenant Admin login issue is still the active debugging task: the user reports the Admin is active, but login through `POST /api/v1/auth/login` returns an unauthorized response. Do not assume the account is inactive; verify the actual persisted user/tenant state and the authentication checks in the latest branch code.
- **Keep authentication routes separate:** `POST /api/v1/auth/login` is for tenant Admin/user login. `POST /api/v1/platform/auth/login` is only for Platform Owner. Do not “fix” this by swapping the Platform Owner UI endpoint.
- **Local Docker state reported by user (2026-10-26):** `docker compose config --services` lists `db` and `api`; database container `contractor-db` was healthy on host port `3308`; API container `contractor-api` maps host `8081` to container `8080`. An unrelated EspoCRM service uses host port `8080`; do not stop or remap it without asking.
- **Resolved runtime symptom:** the user reported that the earlier `GET /api/platform/tenants` 502 issue was resolved (“خلاص اشتغل”). Do not reopen that as an active failure unless it reappears.
- **Current next diagnostic steps:** inspect the exact latest `feature/platform-owner-activation` code for the login service, user-enabled/activation flags, tenant status checks, password encoder, JWT/authentication exception mapping, and relevant integration tests. Then ask for or inspect the actual API log/response from one failed login attempt if the code alone does not explain it. Do not change database rows or reset data as a shortcut.
- **Verification rule:** no fix or test result has been confirmed yet for this specific unauthorized tenant Admin login report. After a code change, run the narrow relevant test first and then the backend suite as appropriate; record only observed outputs.
- **Safety:** preserve existing database data, do not edit applied Flyway migrations, do not merge to `main` without explicit approval, and keep Platform Owner and tenant Admin sessions/roles distinct.

