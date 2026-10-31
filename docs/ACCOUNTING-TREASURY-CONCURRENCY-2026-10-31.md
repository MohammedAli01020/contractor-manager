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

- Latest verified head: `0541a0a8d35cb36d8462dfbbfc9f7a67ad4d1125`; Backend PR CI run [41233999012](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41233999012) passed.
- Maven: **165 tests, 0 failures, 0 errors, 0 skipped**.
- `JournalPostingConcurrencyIntegrationTest`: **8 tests, 0 failures, 0 errors, 0 skipped**.
- `CashBankClassificationMigrationIntegrationTest`: **1 test, 0 failures, 0 errors, 0 skipped**.
- Both dedicated CI guards passed; Docker/MySQL integration tests were executed, not skipped.
- Final review identified and fixed a defensive-construction edge case: the compatibility constructor now infers liquidity classification for codes 1000/1010 only on ASSET accounts. A regression test verifies that a LIABILITY account with code 1000 is not classified as cash/bank.
- V24 was tested only against a disposable MySQL container. No migration was run against a deployed database.

## Review outcome

- Source/diff review completed; the PR changes remain limited to explicit cash/bank classification, treasury locking/payment validation, MySQL migration/concurrency tests, realistic opening-balance fixtures, CI guards, and this documentation.
- Push and pull-request CI both succeeded on `88fd380`; the MySQL integration and V24 migration guards completed successfully.
- PR #17 remains Draft, open, and unmerged, targeting `feature/driver-worker-ledger`. Explicit approval is required before merging; `main` remains untouched.
