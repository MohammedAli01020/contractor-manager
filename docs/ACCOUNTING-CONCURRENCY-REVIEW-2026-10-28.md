# Accounting concurrency review — 2026-10-28

## Scope
Review of accounting journal posting on `fix/accounting-concurrency-hardening`. Do not merge to `feature/financial-foundation` until the remaining acceptance checks are complete.

## Database migration review
Static review of `backend/src/main/resources/db/migration/V17__create_accounting_journal.sql` confirms that it declares:
- Tenant-scoped uniqueness for chart-of-account codes and journal posting keys.
- A unique reversal reference per tenant, preventing a second reversal of the same original entry.
- Composite tenant-scoped foreign keys for journal entries, accounts, users, drivers, equipment, and parent accounts.
- Unique journal line numbering and checks for positive line numbers, one-sided debit/credit amounts, account types, and non-empty required text.

This is a source review, not a claim that a separate clean-database migration-validation job has been run. CI Maven tests passed on the commit listed below; a dedicated migration/startup verification remains an explicit acceptance check.

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

## Transaction propagation and current call sites
`JournalPostingService` uses a `TransactionTemplate` with default `PROPAGATION_REQUIRED`. If called inside an active outer transaction, the template may participate in it; catching `DataIntegrityViolationException` outside the callback does not alone prove that the relevant transaction has fully rolled back before the recovery read.

A source review of the current production Java service/controller files found no production call site for `JournalPostingService.post()`, and no accounting API controller is present in the current Java source tree. Therefore, there is no existing source-operation transaction boundary to validate for this service today. The concern is prospective until the service is wired into a production workflow, but it should be addressed before that integration is shipped.

Do not blindly switch to `REQUIRES_NEW`: independently committing a journal could break atomicity with a source business operation. When adding a production caller, choose and test a transaction design that preserves atomicity and safe idempotent recovery, including the outer-transaction case if applicable.

## Remaining acceptance checks
- [x] Concurrent identical posting-key integration test added; CI passed on the latest branch commit.
- [x] Failed journal-line persistence rollback integration test added; CI passed.
- [x] Concurrent reversal integration test added; CI passed on the latest branch commit.
- [x] Reviewed current production service/controller sources; no production caller of `JournalPostingService.post()` was found.
- [x] Static review of V17 constraints completed.
- [ ] Add or confirm dedicated clean-database Flyway migration/startup validation in CI.
- [ ] Document and verify the process-local login limiter as a known single-instance limitation; do not claim cluster-wide throttling.
- [ ] Before wiring journal posting into a production source workflow, test transaction propagation and preserve atomicity.
- [ ] After all acceptance checks, update this document and Issue #8, then decide whether to merge. Do not modify the base branch or local-only `.gitignore` changes as part of this review.

## Deployment scope
The current deployment target is one backend instance. Redis/shared rate limiting and multi-instance operations are deferred. Any process-local `LoginAttemptRateLimiter` is not cluster-wide protection.
