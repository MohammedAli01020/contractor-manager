# Financial Foundation — Double Entry, Driver Ledger, Expenses and Inventory Integration

**Status:** Design baseline agreed; implementation in progress by phase.  
**Branch:** `feature/financial-foundation`  
**Scope:** Define a durable accounting foundation for a small contractor managing equipment and drivers. This is not a claim that the accounting module is implemented.

## 1. Product decisions

- Use a genuine double-entry journal as the accounting source of truth from the start, but deliver features incrementally rather than attempting a full enterprise ERP in one release.
- Keep operational documents (shifts, expenses, purchases, stock movements, payments) separate from immutable posted journal entries. Each posted business document creates its accounting effect through a single posting service.
- Tenant scope is mandatory on all financial documents, accounts, journals, categories, cash/bank accounts, inventory locations and reporting queries.
- Posted entries are immutable. Corrections use linked reversal/correction entries with reason, actor and timestamp; no silent edits/deletes.
- All posting/payment/close operations are idempotent. A retried request must not create a second financial effect.
- Keep operational result, accrued liabilities, cash movement, stock quantity/value and owner/partner settlements separate.
- Inventory will be a connected module, not a parallel expense system. Purchasing stock increases inventory; issuing/consuming stock transfers its value to the appropriate expense/equipment cost account. Transfers between warehouses do not create an expense.
- Do not assume tax registration, VAT treatment, tax rates, or e-invoice obligations. Keep tax configuration/document references extensible; determine legal treatment with an Egyptian accountant before enabling tax posting/e-invoicing.
- Do not edit applied Flyway migrations; add new migrations only.

## 2. Core accounting model

### 2.1 Chart of accounts

Tenant-owned accounts with code, name, account type, parent (optional), active status, and system-managed flag. Types include asset, liability, equity, revenue and expense. Seed a small default chart per tenant, with guarded system accounts for accounts receivable/payable, inventory, cash/bank, driver payables, equipment operating revenue/cost, and owner/partner settlement liabilities as applicable. Users may add permitted accounts; system accounts cannot be deleted or repurposed after posting.

### 2.2 Journal and lines

A journal entry has tenant, unique posting reference/idempotency key, source document type/id, accounting date/period, description, status, created/posted actor and timestamps, and optional reversal-of reference. Journal lines contain account, debit, credit, optional equipment/driver/supplier/cash account dimensions, and line description. Amounts use fixed-precision decimal, never floating point. Every posted journal must have at least two lines, non-negative debit/credit values, exactly one positive side per line, and total debits equal total credits. Enforce validation in the service and database where practical. Posted journal entries are immutable.

### 2.3 Posting lifecycle

Business documents are drafted/approved, then posted exactly once. Posting and source-document state transition occur atomically in one database transaction. Posting rules map source types to journal lines; API clients cannot submit arbitrary debit/credit lines. A unique constraint on tenant + source type + source ID + posting purpose (or equivalent idempotency key) prevents duplicate posting. Reversal entries reference the original entry and must balance independently.

### 2.4 Driver ledger

Driver statement is a domain view over immutable driver-related source movements and linked accounting entries, not a manually editable balance. Movement types include opening balance, approved shift wage, advance, approved deduction, bonus/allowance, actual payment, adjustment, settlement and reversal. Use one documented sign convention: positive payable balance means the company owes the driver; advances/payments/deductions reduce it. A shift wage is recognized once; the corresponding payable is not deducted twice from the driver's statement. Keep each wage tied to its source shift and snapshot the wage amount.

### 2.5 Equipment expenses and categories

Expense categories are tenant-configurable and include name/code, active state, category purpose, default expense account and whether equipment/shift linking is allowed. Categories may be renamed/deactivated, but historical documents retain a description/accounting snapshot; referenced categories cannot be hard-deleted. Expense documents include incurred/accounting date, supplier/payee (optional), equipment/shift (optional), category, amount, description, attachment reference (optional), approval/posting state and payment allocations. Recognition date is distinct from payment date. Support unpaid, partially paid and fully paid states without recording payment as a second expense.

### 2.6 Cash and payments

Cash/bank accounts are distinct ledger accounts. Payment documents identify direction, account, date, counterparty, amount, method/reference and allocations to one or more payable/receivable/expense documents. Partial payments are supported. Payment reduces the relevant liability/receivable and cash/bank balance; it does not recreate the underlying expense or wage. Transfers between cash/bank accounts move balances without revenue/expense. Reconciliation is a separate controlled workflow.

### 2.7 Inventory integration (Phase 3)

Inventory documents and journal posting share the same posting service:
- Purchase receipt of stock: debit inventory, credit supplier payable (or a receipt-clearing account if invoice and receipt timing differ).
- Supplier payment: debit supplier payable, credit cash/bank.
- Issue/consumption: debit the selected expense/equipment cost account, credit inventory at the approved valuation cost.
- Warehouse transfer: move quantity/location; no company-wide expense or profit effect.
- Return to supplier, stock return, write-off, count adjustment and cancellation each have explicit audited reversal rules.
- Purchase invoices may contain stock, direct equipment expense and general-company expense lines; each line has its own classification and accounting treatment.
- Decide and test weighted-average vs FIFO, negative-stock policy, warehouse model and receipt/invoice timing before implementing stock migrations.
- Never record the same stock item both as an immediate purchase expense and again as an inventory issue expense.

## 3. Equipment result and ownership

Keep the agreed management formula:
**Equipment operating result = completed shift value due − driver wages attributable to those shifts − recognized actual equipment expenses.**

This management report is not itself a journal entry and is not automatically the owner's share. Estimated `operatingCostPerShift` remains a planning indicator; do not post it as an actual expense if fuel/maintenance/stock consumption is separately recorded. Owner/partner settlement is a separate liability/document driven by an explicit agreement, with its own approval and journal posting.

## 4. Monthly close

Close preview must show trial-balance errors, unposted/partially processed documents, unreconciled cash/bank items, inventory exceptions when available, and driver/supplier/owner balances. A close record snapshots tenant, period, approver, timestamp, totals and exception state. Do not close with blocking exceptions. Reopen requires special permission, reason and audit record; corrections after close follow controlled policy and never rewrite prior posted journals silently. Carry forward balances through opening balances/period views, not duplicated transaction rows.

## 5. Phased implementation and exit criteria

### Step A — inspect baseline and establish accounting core
- Recheck current branch, tenant model, money types, Flyway versioning, shift lifecycle and existing expense/inventory code before schema changes.
- Add chart-of-accounts and journal-entry/line model with tenant-safe constraints and a posting service.
- Add automated tests for balanced entries, unbalanced rejection, idempotency, atomic rollback, immutable posted entries, reversal, and cross-tenant denial.
- No tax logic, inventory valuation, or unreviewed automatic historical backfill in this step.

### Step B — driver ledger and configurable equipment expenses
- Add categories and expense documents, driver movements, wage posting from eligible completed shifts, and payment allocations.
- Add statement/report APIs with opening, movement totals and closing balance.
- Tests cover wage-once behavior, advances/payments, partial payments, recognition/payment period separation, category rename/deactivation and correction.

### Step C — cash/bank, reconciliation and monthly close
- Add cash/bank accounts, receipts/payments/transfers, reconciliation preview and controlled close/reopen.
- Validate statements against journal balances and monthly snapshots.

### Step D — purchasing and inventory
- Add suppliers, purchase documents/lines, stock items/units, warehouse model, stock movements and issue-to-equipment integration.
- Decide valuation and negative-stock rules before migrations.
- Tests reconcile quantities, inventory value, supplier liabilities, cash, expense and equipment result for purchase, issue, return, cancellation and retry.

### Step E — reports and future tax integration
- Add equipment/driver/supplier/owner statements, trial balance and management reports, followed by exports.
- Add tax fields and e-invoice integration only after legal/accountant requirements are known.

## 6. Security, data integrity and compatibility

- Every query and mutation derives tenant from authenticated server-side context; never trust a client-provided tenant ID.
- Enforce role permissions in backend services/controllers, not just Flutter UI.
- Use decimal database types and BigDecimal in Java for monetary values.
- Keep source documents and posted journals linked with unique references; add audit actor/time/reason.
- Preserve existing shift snapshot fields and API compatibility. Do not change historic completed shift values when defaults change.
- Use additive Flyway migrations; do not edit migrations already applied.
- Do not post old shifts automatically during initial deployment without an explicit reviewed opening/baseline strategy.
- Run backend tests and relevant Flutter tests; record actual results. No merge to `main` or deployment without explicit approval.

## 7. Open decisions before the inventory phase

1. Inventory valuation: weighted average or FIFO.
2. Warehouse model: one default warehouse first, or multiple warehouses/sites from launch.
3. Negative stock: always blocked, or explicitly permissioned exceptions.
4. Purchase flow: invoice and receipt together, or support goods receipt before invoice from the start.
5. Opening balances and historical data cutover: define a reviewed date and reconciliation process.
6. Tax/accounting policy and invoice obligations: confirm with an Egyptian accountant based on the actual business.
