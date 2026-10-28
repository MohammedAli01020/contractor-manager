# Accounting concurrency review — 2026-10-28

## Scope
Review of the accounting journal posting path on `fix/accounting-concurrency-hardening`.

## Findings

1. `JournalPostingService.post` checks for an existing `(tenant, posting key)` before inserting the new journal. Under concurrent requests, two transactions can both pass the pre-check; the database uniqueness constraint remains the final guard. The losing request may currently surface a conflict rather than return the already-created journal for an identical retry.
2. Reversal posting uses a check-then-insert flow. The database uniqueness constraint must remain the authoritative protection against concurrent attempts to reverse the same entry.
3. Existing `JournalPostingServiceTest` coverage is Mockito-based and does not prove behavior under real MySQL concurrency or transaction rollback.
4. `GlobalExceptionHandler` already maps the known posting-key and reversal unique-constraint names to deterministic HTTP 409 responses. This does not itself make identical concurrent retries idempotent.
5. `.github/workflows/backend-ci.yml` now lists `fix/accounting-concurrency-hardening` in the push branch filters. The pull-request branch filters were left unchanged because they filter the target/base branch, not the source branch.

## Database constraint verification

The current Flyway migration `V17__create_accounting_journal.sql` defines these relevant MySQL/InnoDB constraints:

- `uq_journal_entries_posting_key`: unique `(tenant_id, posting_key)`.
- `uq_journal_entries_reversal`: unique `(tenant_id, reversal_of_entry_id)`. MySQL permits multiple NULL values in a unique index, so ordinary entries can coexist while a given original entry can be reversed at most once per tenant.
- Composite foreign keys keep journal entries and lines tenant-scoped, including the reversal target, journal entry, chart account, driver, and equipment.
- `uq_journal_lines_number`: unique `(journal_entry_id, tenant_id, line_number)`.

These constraints provide the final database-level protection against duplicate posting keys, duplicate reversals, cross-tenant references, and duplicate line numbers. They do not by themselves implement a friendly idempotent response when two identical requests race.

## Next verification steps

- Inspect the actual Flyway migrations and repository constraints to confirm exact unique index names and tenant scoping.
- Add an integration test against MySQL/Testcontainers for concurrent identical posting-key requests and rollback when line persistence fails.
- Define whether a concurrent identical retry should return the existing journal or a deterministic 409; preserve conflicts for a reused key with a different payload.
- Baseline CI run on this branch completed successfully: [Backend CI run #149](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40733388161), commit `dd12425fec1c8363d66f8b7778771b957d6165c7`.
- The captured Maven log reports **135 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. These are the existing tests only; no concurrency or rollback regression test has been added yet.
- Add an integration test against MySQL/Testcontainers for concurrent identical posting-key requests and rollback when line persistence fails.

## Status
- Review documentation and CI push-trigger configuration are committed on the review branch.
- Baseline CI has been verified as passing (135 tests; 0 failures/errors/skips).
- No production code or accounting concurrency tests have been changed yet. Passing baseline tests do not prove concurrent idempotency or rollback atomicity.