# Accounting concurrency review — 2026-10-28

## Scope
Review of accounting journal posting on `fix/accounting-concurrency-hardening`. Do not merge to `feature/financial-foundation` until the remaining transaction-boundary review and all acceptance checks are complete.

## Confirmed database safeguards
Migration `V17__create_accounting_journal.sql` defines the unique posting-key and reversal constraints, along with tenant-scoped foreign keys and journal-line numbering protection. These constraints are the final guard against duplicate keys, duplicate reversals, cross-tenant references, and duplicate line numbers.

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
- CI for that exact commit passed: [run 40738554677](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40738554677). The Maven test step, test summary, and report upload all succeeded. No test count is recorded here because the verified run summary available for this review did not expose a confirmed total.

## Important remaining design review: transaction propagation
`JournalPostingService` currently creates a `TransactionTemplate` with the default `PROPAGATION_REQUIRED`. If `post()` is invoked while an outer transaction is already active, the template may participate in that transaction; catching `DataIntegrityViolationException` outside the callback therefore does not, by itself, prove that the transaction has fully rolled back before the recovery read.

Do not blindly switch to `REQUIRES_NEW`: independently committing a journal could break atomicity with the source business operation. Before merge, inspect all production call sites and their transaction boundaries, then choose and test a design that preserves both idempotent recovery and atomicity with the source operation. Add a regression test for the relevant outer-transaction scenario if such a call path exists.

## Remaining acceptance checks
- [x] Concurrent identical posting-key integration test added; CI passed on the latest branch commit.
- [x] Failed journal-line persistence rollback integration test added; the CI run containing it passed.
- [x] Concurrent reversal integration test added; CI passed on the latest branch commit.
- [ ] Resolve and test the transaction-propagation/outer-transaction concern above.
- [ ] Confirm migration validation and startup behavior in CI without touching a real database.
- [ ] Document the process-local login limiter as a known single-instance limitation; do not claim cluster-wide throttling.
- [ ] After review, update this document and the issue, then decide whether to merge. Do not modify the base branch or local-only `.gitignore` changes as part of this review.

## Deployment scope
The current deployment target is one backend instance. Redis/shared rate limiting and multi-instance operations are deferred, but `LoginAttemptRateLimiter` remains process-local and is not cluster-wide protection.
