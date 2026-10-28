# Session handoff — Accounting safety and idempotency (2026-10-28)

## Repository and working scope
- Repository: https://github.com/MohammedAli01020/contractor-manager
- Current review branch: `feature/financial-foundation` (the earlier implementation work originated on `fix/multi-instance-accounting-safety`).
- Tracking issue: https://github.com/MohammedAli01020/contractor-manager/issues/8
- Do not merge to or modify `main` as part of this work.
- Preserve the user's local-only `.gitignore` change; verify local Git status before any commands that could overwrite or discard changes.
- Do not run Flyway migrations against a real database or deploy without explicit approval.

## Deployment decision
The immediate target is **one backend instance**. Prioritize accounting correctness, tenant isolation, transaction boundaries, database constraints, and deterministic duplicate-request handling now. Defer Redis/shared rate limiting, load balancer setup, and multi-instance operations until there is a concrete need. The current in-memory login rate limiter must not be described as cluster-wide.

## Work recorded on the branch
- `42b9138` — map unique posting-key and duplicate-reversal constraint violations to explicit HTTP 409 responses.
- `3ac57b9` — include this work branch in Backend CI push filters.
- `4249398` — add a unit test rejecting reuse of an existing posting key with a different payload.
- `e7c1314` — add a unit test that an identical retry returns the existing journal without persisting duplicate journal/lines.

Full commit references:
- `42b9138682d5732c50ed52aebad76ec9112db176`
- `3ac57b969efc2c405208ce405f9fa3a71dbd8560`
- `4249398ed4745d5fcba9619ec5c774941a1ca0a4`
- `e7c1314ebc238e23aa3c24117086cd082846f51c`

## Verification recorded
- Backend CI / Maven test step succeeded for `4249398ed4745d5fcba9619ec5c774941a1ca0a4`.
- Backend CI / Maven test step succeeded for `e7c1314ebc238e23aa3c24117086cd082846f51c`.
- Recorded workflow run: https://github.com/MohammedAli01020/contractor-manager/actions/runs/40729525298

These results verify the reported unit-test/CI runs only. They do **not** establish correct behavior under real concurrent database requests, transaction rollback under persistence failure, or migration safety.

## Next work — continue in this order
1. Inspect the current branch source and diff before editing; confirm the service, tests, exception handler, and migration definitions match the progress notes.
2. Add/verify database-backed tests for concurrent requests using the same tenant + posting key:
   - identical normalized payload should resolve to one journal and a deterministic replay result;
   - different payload should produce a deterministic conflict;
   - no duplicate or partial journal lines.
3. Add/verify concurrent reversal tests so only one reversal can be created and the losing request receives a deterministic result.
4. Verify transaction rollback if journal-line persistence fails after the header is written.
5. Use MySQL/Testcontainers integration tests if supported by the existing test setup; distinguish skipped tests from passed tests.
6. Review Flyway migration validation/startup behavior without running migrations against a real database.
7. Re-run tests/CI and update this handoff and Issue #8 with exact commit and verification evidence.

## Important technical cautions
- A unique constraint on `(tenant_id, posting_key)` prevents duplicate journal headers but alone does not guarantee concurrent losers return the winning journal; a loser may still receive a conflict unless the service explicitly handles the race.
- The unique reversal reference protects integrity but the losing concurrent request still needs deterministic error/replay handling.
- Confirm the journal header and all lines are committed or rolled back in one transaction.
- Do not claim multi-instance readiness, full concurrency safety, or migration safety based only on unit-test success.


## Follow-up review — 2026-10-28

- Latest Backend CI run on `feature/financial-foundation`: [run 40745882282](https://github.com/MohammedAli01020/contractor-manager/actions/runs/40745882282), commit `28a05b53d571db16ccd068de23e8d311242d071a`; overall result **success**.
- The run's Maven test step and `Require MySQL accounting integration tests to execute` guard both passed. `JournalPostingConcurrencyIntegrationTest` contains database-backed scenarios for concurrent identical posting, concurrent reversals, and rollback when journal-line persistence fails.
- `JournalPostingService` uses a transaction template, normalizes payloads for idempotent replay, and handles posting-key/reversal integrity races after rollback. The migration defines tenant-scoped unique keys and foreign keys. `application.yml` uses Hibernate `ddl-auto: validate`; Flyway owns schema changes.
- `LoginAttemptRateLimiter` documents that it is **per-process**. Shared/Redis throttling and multi-instance deployment remain explicitly deferred while the deployment target is one backend instance.
- Repository review found no production caller of `JournalPostingService.post()` and no current expense/income source workflow in the accounting package. This is not evidence of a failing test; it means the service is currently a foundation, not yet integrated into a source-document workflow.

## Required before integrating source workflows

When an expense, receipt, invoice, or other source workflow begins calling `JournalPostingService.post()`, add an integration test proving the source-document write and journal posting have the intended atomicity: a failure must not leave a source document without its required journal, or a journal without its source. Choose the transaction boundary based on the actual caller and propagation requirements; do not add `REQUIRES_NEW` without evidence. This integration criterion remains a prerequisite for wiring production source workflows, rather than a claim that such a workflow currently exists.

## Current review disposition

Issue #8 has been updated with the latest CI evidence and the source-workflow caveat. Do not claim multi-instance readiness or close the issue solely on the basis of CI success; close it only after confirming the remaining issue acceptance criteria and explicitly deciding whether source-workflow atomicity is deferred until a source workflow exists. No changes to `main`; preserve the user's local-only `.gitignore` change and never run migrations against a real database without approval.
