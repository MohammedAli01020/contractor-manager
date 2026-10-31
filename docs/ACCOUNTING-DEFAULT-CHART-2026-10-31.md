# Default chart of accounts and journal linkage

**Branch:** `feature/accounting-default-chart-completion`  
**Base:** `fix/treasury-concurrency-hardening`  
**Status:** Implemented in this branch; CI verification pending. No merge or production migration performed.

## Scope

- Centralize the platform-owned account catalog in `SystemAccountCode`; account codes are stable identifiers, while database account IDs remain tenant-specific.
- Keep tenant initialization idempotent and non-destructive: create missing defaults only, preserving existing names, active state and custom accounts.
- Add the inventory asset account (1400) as a chart foundation for the later inventory phase. This does not implement inventory movement or stock valuation.
- Resolve required system accounts inside the tenant and reject missing, inactive, non-system-managed, wrongly typed or wrongly classified accounts before a business workflow posts a journal.
- Use the same account resolver for shift wage accrual/recoverable advance recovery, driver payments/advances and supplier expense/payment postings. The resolved tenant account ID is then passed to the existing `JournalPostingService`, which remains the only journal-posting entry point.

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
