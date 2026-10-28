# Accounting concurrency review — 2026-10-28

## Scope
Review of the accounting journal posting path on `fix/accounting-concurrency-hardening`.

## Findings

1. `JournalPostingService.post` checks for an existing `(tenant, posting key)` before inserting the new journal. Under concurrent requests, two transactions can both pass the pre-check; the database uniqueness constraint remains the final guard. The losing request may currently surface a conflict rather than return the already-created journal for an identical retry.
2. Reversal posting uses a check-then-insert flow. The database uniqueness constraint must remain the authoritative protection against concurrent attempts to reverse the same entry.
3. Existing `JournalPostingServiceTest` coverage is Mockito-based and does not prove behavior under real MySQL concurrency or transaction rollback.
4. `GlobalExceptionHandler` already maps the known posting-key and reversal unique-constraint names to deterministic HTTP 409 responses. This does not itself make identical concurrent retries idempotent.
5. `.github/workflows/backend-ci.yml` does not currently list `fix/accounting-concurrency-hardening` in the push branch filters. Its pull-request branch filters also do not include this new review branch as a base target. Confirm intended PR base before adjusting the workflow.

## Next verification steps

- Inspect the actual Flyway migrations and repository constraints to confirm exact unique index names and tenant scoping.
- Add an integration test against MySQL/Testcontainers for concurrent identical posting-key requests and rollback when line persistence fails.
- Define whether a concurrent identical retry should return the existing journal or a deterministic 409; preserve conflicts for a reused key with a different payload.
- Add the review branch to CI push filters (and only add it to PR base filters if it is intended to be a target branch).
- Run the backend test suite and inspect the resulting CI run before proposing a merge.

## Status
Review notes only. No production code or test behavior was changed by this document. No test run is claimed.