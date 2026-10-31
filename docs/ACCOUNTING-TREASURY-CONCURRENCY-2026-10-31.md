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

- Previous verified head: `9944b4a030f4d0b3864ec56f5209b7e15f393197`.
- Previous successful Backend CI run: [41224106120](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41224106120).
- On that previous head, Maven reported **163 tests, 0 failures, 0 errors, 0 skipped**.
- On that previous head, the required MySQL concurrency suite reported **8 tests, 0 failures, 0 errors, 0 skipped**.
- Those results apply only to `9944b4a...`; they do not verify the migration-upgrade test or the newer commits below.
- Added `CashBankClassificationMigrationIntegrationTest`: it migrates an isolated disposable MySQL database through V23, inserts representative existing tenant/account rows, applies V24, and verifies:
  - Existing ASSET accounts 1000 and 1010 are classified as cash/bank.
  - Other account codes remain unclassified.
  - A 1000 LIABILITY account remains unclassified.
  - The classification is tenant-scoped (including an existing 1010 account for another tenant).
  - Existing account data and row count are unchanged.
- Backend CI now has a separate guard requiring this V24 migration test to execute exactly once with zero skips, failures, or errors.
- The new migration test and CI guard are committed on this branch; their CI result is **pending**. Do not treat the previous 163/8 results as validation of the new head.
- PR #17 remains Draft and unmerged. No production database was used for migration testing.

## Next steps

1. Confirm CI result and inspect any failing test logs.
2. Fix issues on this branch only, then rerun CI.
3. Review the final PR diff and migration ordering.
4. Keep PR #17 in Draft until all checks and review are complete.
5. Ask for explicit approval before merging into `feature/driver-worker-ledger`; do not merge to `main`.
