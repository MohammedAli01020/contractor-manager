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

- PR #17 was opened as Draft to trigger the repository's Backend CI.
- Backend CI was observed running on commit `dd478e90a1d1ecb124b8ef38f61d99495fe0a8fd`; Maven tests and the required MySQL integration-test guard had not yet reported a final result when this note was written.
- Do not describe this branch as verified until both the Maven step and the guard requiring non-skipped MySQL concurrency tests pass.
- CI workflow now includes this working branch and `feature/driver-worker-ledger` as a pull-request base so the checks run for this increment.

## Next steps

1. Confirm CI result and inspect any failing test logs.
2. Fix issues on this branch only, then rerun CI.
3. Review the final PR diff and migration ordering.
4. Keep PR #17 in Draft until all checks and review are complete.
5. Ask for explicit approval before merging into `feature/driver-worker-ledger`; do not merge to `main`.
