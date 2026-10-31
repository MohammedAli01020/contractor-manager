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

- Verified head: `bb2d358fb1bd89202551ed4465190895f253508f`.
- Successful Backend CI push run: [41225181346](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41225181346).
- Successful Backend CI pull-request run: [41225186663](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41225186663).
- Downloaded Surefire reports from run 41225181346 and independently aggregated all 29 test-suite XML reports: **164 tests, 0 failures, 0 errors, 0 skipped**.
- `JournalPostingConcurrencyIntegrationTest`: **8 tests, 0 failures, 0 errors, 0 skipped**.
- `CashBankClassificationMigrationIntegrationTest`: **1 test, 0 failures, 0 errors, 0 skipped**. The test migrated a disposable MySQL database through V23, inserted representative existing tenant/account data, applied V24, and verified the intended classifications and unchanged existing account data.
- Both CI runs passed the dedicated guards requiring the MySQL accounting integration tests and V24 existing-data migration test to execute; neither required test was skipped.
- The earlier fixture-ID failure was fixed by retrieving tenant IDs from the same insert operation via `GeneratedKeyHolder`.
- No migration was run against a real/deployed database.
- PR #17 remains Draft and unmerged. No changes were made to `main`.

## Next steps

1. Complete the final source/diff review and confirm the PR metadata remains unchanged.
2. Keep PR #17 in Draft until the review is complete.
3. Ask for explicit approval before merging into `feature/driver-worker-ledger`; do not merge to `main`.

1. Confirm CI result and inspect any failing test logs.
2. Fix issues on this branch only, then rerun CI.
3. Review the final PR diff and migration ordering.
4. Keep PR #17 in Draft until all checks and review are complete.
5. Ask for explicit approval before merging into `feature/driver-worker-ledger`; do not merge to `main`.
