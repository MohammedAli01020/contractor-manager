# Accounting concurrency review — 2026-10-28

## Scope
Review of accounting journal posting on `fix/accounting-concurrency-hardening`. Do not merge to `feature/financial-foundation` until the remaining acceptance checks are complete.

## Database migration review
Static review of `backend/src/main/resources/db/migration/V17__create_accounting_journal.sql` confirms that it declares:
- Tenant-scoped uniqueness for chart-of-account codes and journal posting keys.
- A unique reversal reference per tenant, preventing a second reversal of the same original entry.
- Composite tenant-scoped foreign keys for journal entries, accounts, users, drivers, equipment, and parent accounts.
- Unique journal line numbering and checks for positive line numbers, one-sided debit/credit amounts, account types, and non-empty required text.

### Fresh-database startup verification
The integration-test base class starts a disposable `mysql:8.0` Testcontainers database with a new `contractor_test` schema. The tests use `@SpringBootTest` with the `test` profile; no test-specific application configuration overrides Flyway. The main application config enables Flyway at `classpath:db/migration` and sets Hibernate schema handling to `validate`. Consequently, a successful Spring context startup for these integration tests exercises Flyway migrations against a fresh MySQL schema and Hibernate schema validation, rather than an H2 approximation.

The integration-test base deliberately skips tests if Docker is unavailable. To prevent a green CI run that silently skipped this critical path, `.github/workflows/backend-ci.yml` now has a guard that requires the accounting concurrency integration-test report to exist, contain tests, and report zero skipped tests. This is an automated startup/migration check through the integration-test application context; it is not a separate standalone `flyway validate` job. The guard has been committed and its newly triggered CI result must be checked before marking this acceptance item complete.

## Changes and verification

### Baseline
- Commit: `dd12425fec1c8363d66f8b7778771b957d6165c7`
- CI: [run 40733388161](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40733388161)
- Captured baseline log: 135 tests, 0 failures, 0 errors, 0 skipped. This predates the new regression tests.

### Regression tests
- Commit `8fef45a3a038fd6194d2941f69bcabfde73e7f98` added integration coverage for concurrent identical posting requests and rollback when line persistence fails.
- The first concurrency run failed with `DataIntegrityViolationException` on `uq_journal_entries_posting_key`, demonstrating the check-then-insert race.
- Commit `014275f7ea7e0f97e23bb25b0415d982377835d4` added recovery after a database integrity violation. CI passed: [run 40738038729](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40738038729).
- Commit `d71f75df29e765a241d62ec931ac6457970c7909` includes `concurrentReversalsCreateAtMostOneReversal`, which exercises two concurrent reversal requests with distinct posting keys.
- CI for that exact commit passed: [run 40738554677](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40738554677). The Maven test step, test summary, and report upload all succeeded. No test count is recorded here because no confirmed total was captured in the verified run details.
- Commit `9258c32db339556bb3b83f4787de345d18b20c1f` adds a CI guard against skipped/missing accounting MySQL integration tests. CI passed, including the guard: [run 40740171474](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40740171474).

## Transaction propagation and current call sites
`JournalPostingService` uses a `TransactionTemplate` with default `PROPAGATION_REQUIRED`. If called inside an active outer transaction, the template may participate in it; catching `DataIntegrityViolationException` outside the callback does not alone prove that the relevant transaction has fully rolled back before the recovery read.

A source review of the current production Java service/controller files found no production call site for `JournalPostingService.post()`, and no accounting API controller is present in the current Java source tree. Therefore, there is no existing source-operation transaction boundary to validate for this service today. The concern is prospective until the service is wired into a production workflow, but it should be addressed before that integration is shipped.

Do not blindly switch to `REQUIRES_NEW`: independently committing a journal could break atomicity with a source business operation. When adding a production caller, choose and test a transaction design that preserves atomicity and safe idempotent recovery, including the outer-transaction case if applicable.

## Remaining acceptance checks
- [x] Concurrent identical posting-key integration test added; CI passed.
- [x] Failed journal-line persistence rollback integration test added; CI passed.
- [x] Concurrent reversal integration test added; CI passed.
- [x] Reviewed current production service/controller sources; no production caller of `JournalPostingService.post()` was found.
- [x] Static review of V17 constraints completed.
- [x] Confirmed integration tests use a fresh disposable MySQL 8 database and Spring application startup enables Flyway plus Hibernate schema validation.
- [x] Verified CI for commit `9258c32db339556bb3b83f4787de345d18b20c1f`: Maven tests and the new guard passed; the accounting integration tests were executed and none were skipped.
- [ ] Document and verify the process-local login limiter as a known single-instance limitation; do not claim cluster-wide throttling.
- [ ] Before wiring journal posting into a production source workflow, test transaction propagation and preserve atomicity.
- [ ] After all acceptance checks, update this document and Issue #8, then decide whether to merge. Do not modify the base branch or local-only `.gitignore` changes as part of this review.

## Deployment scope
The current deployment target is one backend instance. Redis/shared rate limiting and multi-instance operations are deferred. Any process-local `LoginAttemptRateLimiter` is not cluster-wide protection.
