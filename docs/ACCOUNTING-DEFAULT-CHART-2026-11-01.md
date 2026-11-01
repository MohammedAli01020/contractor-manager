# Default chart of accounts and system-account resolution

**Implementation branch:** `feature/default-chart-reconciliation`  
**Base:** `feature/driver-worker-ledger`  
**Status:** Reconciliation implementation in progress; fresh CI and full diff review are pending. This document does not mark the work as accepted or merged.

## Scope

- Centralize the 15 tenant default/system accounts in `SystemAccountCode`, including inventory asset account 1400.
- Initialize only missing defaults. Preserve tenant-specific names, active flags, custom accounts and existing account IDs.
- When defaults are missing, lock the tenant row and perform a locking/current read of chart rows before inserting. This prevents concurrent initializers from creating duplicate defaults under MySQL REPEATABLE READ.
- Resolve system accounts within the tenant and validate active state, system-managed flag, account type and cash/bank classification before journal posting.
- Keep `JournalPostingService` as the single journal-posting entry point.

## Operational posting map

| Workflow | Debit | Credit |
|---|---|---|
| Shift wage | 5000 — driver wage expense | 2100 — driver payable |
| Recoverable worker advance | 1300 — recoverable advances | selected cash/bank |
| Advance against earned payable | 2100 — driver payable | selected cash/bank |
| Driver payment | 2100 — driver payable | selected cash/bank |
| Expense recognition | selected expense account | 2000 — supplier payable |
| Expense payment | 2000 — supplier payable | selected cash/bank |

All account lookups are tenant-scoped and the server supplies the resolved tenant-local account IDs.

## Compatibility and integrity requirements

- Preserve the financial idempotency reservation and request-fingerprint checks in `ExpenseService` and `DriverLedgerService`. Replacing these with source-table duplicate lookups alone is not acceptable.
- Preserve completed-shift immutability, including completed zero-wage shifts, in both service logic and database protections. A completed shift with no wage journal must not become editable or cancellable.
- Initialize missing defaults before paying legacy expenses, because an expense may predate the default supplier-payable account.
- Do not call default initialization twice in the same expense-create transaction.
- Preserve all existing CI guards for V24 populated-data migration, journal concurrency, financial idempotency and completed zero-wage shift protections. Add required guards for the default-chart unit and MySQL concurrency tests.
- Inventory account 1400 is only a chart entry. Inventory valuation, stock movements and issue posting remain Phase 3 work.

## Data and release boundaries

- No existing Flyway migration was edited; no production migration was applied for this feature.
- No historical expense or shift is automatically backfilled or reposted.
- Existing posted journals remain immutable; corrections must use the established reversal/correction workflow.
- This branch is a reviewable reconciliation branch. PR #18 remains open and unmerged until the resulting code and CI evidence are reviewed. PR #19 remains dependent on the final PR #18 base/head relationship.

## Concurrent balance-read isolation

Balance-sensitive driver operations use the driver row as the serialization lock. They explicitly run at MySQL `READ COMMITTED` isolation because lazy default-chart initialization can execute a consistent read before the driver lock is acquired. Under the default `REPEATABLE READ` isolation, a later aggregate balance query could reuse that older snapshot after waiting for the lock and miss the previous transaction's committed payment or recovery. `READ COMMITTED` gives each post-lock balance query a fresh committed view without loading and locking the driver's entire ledger history. The same isolation is used by shift completion when it calculates recoverable-advance recovery after locking the driver.
