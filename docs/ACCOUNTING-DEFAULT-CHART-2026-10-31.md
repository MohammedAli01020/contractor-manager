# Default chart of accounts and journal linkage

**Branch:** `feature/accounting-default-chart-completion`  
**Base:** `fix/treasury-concurrency-hardening`  
**Status:** Implemented. Branch synchronization and fresh CI verification completed on head `9a6fe8c90d061afc17b0aeab24b65a9483ffa90e`; both Push run [41243795829](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41243795829) and PR run [41243799919](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41243799919) succeeded. Each run reported **170 tests, 0 failures, 0 errors, 0 skipped**. PR #18 remains Draft and unmerged. No production migration performed.

## 2026-10-31 branch synchronization and verification checkpoint

- PR #18 base branch advanced from `97daa6101450eae7e5c5591738c0d0b37fbb63da` to `0541a0a8d35cb36d8462dfbbfc9f7a67ad4d1125`. Before synchronization, GitHub reported the PR branch as 5 commits behind with a merge conflict.
- Created merge commit `001a00b129d9ec0e76ef1826dde12aa5062f793f` on `feature/accounting-default-chart-completion`, with the PR head as first parent and the latest base as second parent. This was a branch-only synchronization; neither `main` nor any PR merge state was changed.
- Preserved the base-branch `ChartAccount` defensive classification fix and its regression test, treasury concurrency documentation, and workflow triggers. Preserved the PR's default-chart tests, concurrency integration test and required CI guard. A follow-up workflow-only cleanup removed a duplicate documentation path entry.
- Final branch head: `9a6fe8c90d061afc17b0aeab24b65a9483ffa90e`. GitHub comparison reports **18 commits ahead / 0 behind**, `mergeable=true`; PR #18 remains open and Draft.
- Fresh Backend CI on this exact head:
  - Push run [41243795829](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41243795829): success; **170 tests, 0 failures, 0 errors, 0 skipped**.
  - Pull-request run [41243799919](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41243799919): success; **170 tests, 0 failures, 0 errors, 0 skipped**.
  - `DefaultChartOfAccountsConcurrencyIntegrationTest`: **1 test, 0 failures, 0 errors, 0 skipped**; CI required-suite guard passed.
  - `JournalPostingConcurrencyIntegrationTest`: **8 tests, 0 failures, 0 errors, 0 skipped**.
  - `CashBankClassificationMigrationIntegrationTest`: **1 test, 0 failures, 0 errors, 0 skipped**.
  - `DefaultChartOfAccountsServiceTest`: **9 tests, 0 failures, 0 errors, 0 skipped**.
- No PR was merged. PR #18 and its dependency PR #17 remain subject to separate review/approval. No production migration was run.

## Next session

1. Review PR #18's final diff and dependency PR #17 status before changing review readiness.
2. Inspect PR #19 because it is stacked on PR #18; refresh its base relationship and determine the safest synchronization after reviewing the diff.
3. Revisit the overlapping default-chart API work in PR #12 and PR #14, settle one API contract, then reassess PR #15's mobile dependency.
4. Keep all PRs unmerged and do not modify `main` without explicit approval.

## Scope

- Centralize the platform-owned account catalog in `SystemAccountCode`; account codes are stable identifiers, while database account IDs remain tenant-specific.
- Keep tenant initialization idempotent and non-destructive: create missing defaults only, preserving existing names, active state and custom accounts. If defaults are missing, serialize initialization by locking the tenant row and re-reading after the lock; fully initialized tenants do not take that lock.
- Add the inventory asset account (1400) as a chart foundation for the later inventory phase. This does not implement inventory movement or stock valuation.
- Resolve required system accounts inside the tenant and reject missing, inactive, non-system-managed, wrongly typed or wrongly classified accounts before a business workflow posts a journal.
- Use the same account resolver for shift wage accrual/recoverable advance recovery, driver payments/advances and supplier expense/payment postings. The resolved tenant account ID is then passed to the existing `JournalPostingService`, which remains the only journal-posting entry point.
- MySQL integration assertions verify that shift wages debit 5000 and credit 2100, and supplier expense/payment journals use the configured expense, supplier-payable and cash/bank accounts.

## Default catalog

| Code | Account | Type | Cash/bank |
|---|---|---|---|
| 1000 | الخزينة | ASSET | Yes |
| 1010 | البنوك | ASSET | Yes |
| 1100 | العملاء والمستحقات | ASSET | No |
| 1200 | الأصول الثابتة | ASSET | No |
| 1300 | سلف العاملين القابلة للاسترداد | ASSET | No |
| 1400 | المخزون | ASSET | No |
| 2000 | الموردون | LIABILITY | No |
| 2100 | مستحقات السائقين | LIABILITY | No |
| 2200 | مستحقات الملاك والشركاء | LIABILITY | No |
| 3000 | رأس المال | EQUITY | No |
| 4000 | إيرادات الورديات | REVENUE | No |
| 4100 | إيرادات أخرى | REVENUE | No |
| 5000 | مصروف أجور السائقين | EXPENSE | No |
| 5100 | مصروفات تشغيل المعدات | EXPENSE | No |
| 5200 | مصروفات أخرى | EXPENSE | No |

## Posting map

- Completed shift wage: debit 5000, credit 2100.
- Recoverable worker advance: debit 1300, credit selected cash/bank.
- Advance against earned payable: debit 2100, credit selected cash/bank.
- Driver payment: debit 2100, credit selected cash/bank.
- Expense recognition: debit selected EXPENSE account, credit 2000.
- Expense payment: debit 2000, credit selected cash/bank.

All journal lines continue to use tenant-local account IDs obtained server-side. Client-supplied tenant IDs are not used for account resolution.

## Safety boundaries

- No existing Flyway migration was edited and no new migration was added for this change.
- Existing tenant-specific account names and custom accounts are not overwritten.
- No historical shifts or expenses are automatically posted or backfilled.
- Account 1400 is only a default catalog entry; inventory purchasing, valuation and issue posting remain Phase 3 work.
- Posted journals remain immutable and corrections continue to use the existing reversal workflow.
- PR #17 remains open and Draft; this branch is stacked on its head and does not modify `main`.
