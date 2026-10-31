# Treasury and bank concurrency hardening — 2026-10-31

## Scope and safety

- Working branch: `fix/treasury-concurrency-hardening`
- Base branch: `feature/driver-worker-ledger`
- Pull request: [#17](https://github.com/MohammedAli01020/contractor-manager/pull/17) (Draft)
- Do not merge this PR or modify `main` without explicit approval.
- Do not run Flyway migrations against a real/deployed database as part of this review.

## Findings from source review

- `JournalPostingService` already locks cash/bank accounts with `PESSIMISTIC_WRITE`, sorts account IDs before acquiring multiple locks, calculates the current balance inside the posting transaction, and rejects a negative resulting balance.
- The original lock query identified liquidity accounts only by account codes `1000` and `1010`.
- `DriverLedgerService` and `ExpenseService` independently repeated the same hard-coded account-code restriction.
- This would make a future additional bank or cash account bypass the common lock/floor mechanism or be rejected by the source workflow.

## Changes on this branch

- Added `chart_of_accounts.cash_bank_account` as an explicit account-purpose flag.
- Added additive Flyway migration `V24__classify_cash_bank_accounts.sql`:
  - Adds the flag with a safe `FALSE` default.
  - Backfills existing asset accounts `1000` and `1010` as liquidity accounts.
  - Adds a check that a liquidity account must be an `ASSET`.
  - Adds an index for tenant-scoped liquidity account lookup.
- Updated the entity and default chart initialization to classify the default cash and bank accounts explicitly.
- Updated journal account locking to lock any account explicitly classified as cash/bank, not only two magic account codes.
- Updated driver payments, worker advances, and expense payments to validate the explicit classification.
- Preserved deterministic ascending account-ID locking for journals that touch more than one liquidity account.
- Added MySQL integration cases for:
  - Concurrent outflows against both default cash and bank balances.
  - Opposite-direction transfers between cash and bank.
  - Concurrent outflows against an additional bank account with a non-default code.
- Extended default-chart unit coverage to assert only the intended default accounts are marked as liquidity accounts.

## Verification status

- Verified head after final documentation correction: `88fd380ade49eb2506be9b7d886eb840af9dbd58`.
- Successful Backend CI push run on the preceding source-and-documentation head: [41225452808](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41225452808).
- Successful Backend CI pull-request run on the preceding source-and-documentation head: [41225457241](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41225457241).
- The Surefire artifact from the verified `88fd380` head was independently aggregated: 29 test-suite XML reports, **164 tests, 0 failures, 0 errors, 0 skipped**. Push and PR workflows [41225793226](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41225793226) and [41225797046](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41225797046) both succeeded on this exact SHA.
- `JournalPostingConcurrencyIntegrationTest`: **8 tests, 0 failures, 0 errors, 0 skipped**.
- `CashBankClassificationMigrationIntegrationTest`: **1 test, 0 failures, 0 errors, 0 skipped**. The test migrated a disposable MySQL database through V23, inserted representative existing tenant/account data, applied V24, and verified the intended classifications and unchanged existing account data.
- Both CI runs passed the dedicated guards requiring the MySQL accounting integration tests and V24 existing-data migration test to execute; neither required test was skipped.
- The earlier fixture-ID failure was fixed by retrieving tenant IDs from the same insert operation via `GeneratedKeyHolder`.
- No migration was run against a real/deployed database.
- PR #17 remains Draft and unmerged. No changes were made to `main`.

## Review outcome

- Source/diff review completed; the PR changes remain limited to explicit cash/bank classification, treasury locking/payment validation, MySQL migration/concurrency tests, realistic opening-balance fixtures, CI guards, and this documentation.
- Push and pull-request CI both succeeded on `88fd380`; the MySQL integration and V24 migration guards completed successfully.
- PR #17 remains Draft, open, and unmerged, targeting `feature/driver-worker-ledger`. Explicit approval is required before merging; `main` remains untouched.
