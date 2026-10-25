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

## Exact next steps

1. Check the current local Git state before editing:
   ```bat
   cd /d D:\\contractor-manager
   git status -sb
   git log -1 --oneline
   ```
2. Open and inspect:
   `backend/src/test/java/com/contractor/app/platform/PlatformOwnerActivationIntegrationTest.java`
3. Add focused tests for the security cases above. Do not change application code unless a test demonstrates a real issue; explain the proposed code change before making it.
4. Run the targeted integration test:
   ```bat
   cd /d D:\\contractor-manager\\backend
   mvn "-Dtest=PlatformOwnerActivationIntegrationTest" test
   ```
5. Run the full backend suite:
   ```bat
   mvn test
   ```
6. Record the actual results here and update `docs/platform-owner-activation.md` and the roadmap only when supported by test evidence.
7. Review `git diff` and `git status`; commit and push only after explicit user approval. Do not merge into `main` without explicit approval.

## Working rules

- Focus on the **backend** unless the user asks otherwise; do not start Flutter work by assumption.
- Work in small, explained steps and provide copy-paste Windows CMD commands when useful.
- Never claim a test passed unless its output confirms it.
- Do not edit previously-applied Flyway migrations.
- Before resuming in a new chat, read this file, then verify the actual branch, commit, working tree, and relevant test output. This note is a handoff aid, not a substitute for checking the repository.
