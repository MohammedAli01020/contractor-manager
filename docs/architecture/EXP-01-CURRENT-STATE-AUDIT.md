# EXP-01 — Current Backend Foundation Audit

**Audit date:** 2026-11-06  
**Audited ref:** `integration/stabilization-2026-11` at `a63e49b7a100f32af39d0cf5a20575cb512ba506`  
**Related specification:** [Expense Foundation](EXPENSE-FOUNDATION-2026-11.md)  
**Status:** Initial source inspection; full audit and tests remain open

## 1. Executive summary

The current backend already has a meaningful and reusable accounting foundation:
- Tenant-scoped expense source records and separate payment records.
- Double-entry journal posting through a shared posting service.
- Expense and payment posting keys/idempotency.
- Supplier-payable recognition followed by payment settlement.
- Immutable posted journal/source records guarded by database triggers.
- Cash/bank account locking and non-negative liquidity checks in journal posting.
- Expense-payment serialization through a tenant-scoped pessimistic lock on the source expense.

This is a strong base. The expense feature should be extended, not replaced with a second ledger or a parallel treasury.

However, the current expense source model is intentionally narrow and does not yet meet the full SaaS specification: one header amount/account/category, supplier as free text, at most one equipment and one shift reference, no expense line model, no explicit allocation records, and no demonstrated approval lifecycle or inventory subsystem in the audited backend package tree.

## 2. Verified existing implementation

### 2.1 Expense source

Evidence:
- `expense/domain/Expense.java`
- `expense/api/ExpenseController.java`
- `expense/api/dto/CreateExpenseRequest.java`
- `expense/api/dto/ExpenseResponse.java`
- `expense/infra/ExpenseRepository.java`
- `V18__create_expenses_and_payments.sql`

Current source fields include:
- Tenant, generated expense number, client request key.
- Expense date, category code, description, optional free-text supplier name.
- One positive amount and one expense account.
- Optional equipment ID and optional shift ID.
- Linked journal ID and creator.

Database uniqueness includes tenant + expense number, tenant + client request key, and tenant + journal link. Composite tenant foreign keys protect account/equipment/shift/user/journal references.

### 2.2 Expense posting

`ExpenseService.create` currently:
1. Reserves a tenant-scoped financial idempotency key and request fingerprint.
2. Replays an identical prior request or rejects reuse with different data.
3. Initializes/validates the tenant chart of accounts and requires an EXPENSE account.
4. Creates the expense source row.
5. Posts a journal through `JournalPostingService`:
   - Debit the selected expense account.
   - Credit the system supplier-payable account.
6. Links the journal to the source row in the same service transaction.

Thus the current create endpoint recognizes an expense and supplier payable immediately. It does not expose a draft/submit/approve/post lifecycle in the inspected controller/service.

### 2.3 Payments

`ExpenseService.addPayment`:
- Locks the tenant-scoped expense row with `findByIdAndTenantIdForUpdate`.
- Uses a tenant-scoped idempotency key and fingerprint.
- Sums prior payments and rejects overpayment.
- Requires a cash/bank asset account.
- Creates a separate payment source record.
- Posts debit to supplier payable and credit to cash/bank.
- Links the payment journal to the payment record.

The integration tests cover partial payment, idempotent retry, overpayment rejection, balanced journals, and database immutability. The service therefore already avoids re-recognizing expense when an existing expense is paid.

### 2.4 Journal posting

`accounting/domain/JournalPostingService.java` is the shared posting entry point. It validates:
- Tenant and actor identity.
- Posting key/source identity.
- Accounting date and at least two journal lines.
- Positive debit/credit side per line and balanced totals.
- Tenant ownership and active state of accounts and linked driver/equipment.
- Cash/bank locks and non-negative balance floors.
- Duplicate posting replay and reversal uniqueness/consistency.

`V17` defines the double-entry chart/journal schema. `V23` protects posted journals from mutation. `V26` protects posted expenses and payments from financial updates/deletion, while permitting the one-time source-to-journal link when it matches the exact posting source.

### 2.5 Existing tests

`ExpenseIntegrationTest` verifies:
- Automatic balanced expense journal creation.
- Partial payment and same-key retry without a duplicate payment journal.
- Overpayment rejection.
- Database rejection of direct mutation/deletion and invalid journal linking for posted source/payment records.

The full audit must still inspect all concurrency tests, trigger behavior, account floor semantics, and current CI gates before any design-to-schema mapping is approved.

## 3. Current gaps against the target design

| Area | Current evidence | Gap / required decision |
|---|---|---|
| Expense lines | One amount/account per expense header | Add line model only after deciding backward-compatible migration/API strategy |
| Supplier | Free-text `supplier_name` | Supplier master, duplicate matching, and supplier-level payable statement not demonstrated |
| Categories | Free-text `category_code` | Tenant-managed category catalog and safe historical deactivation not demonstrated |
| Operational links | One nullable equipment ID and one nullable shift ID | No multi-target allocation model; inspect and validate equipment/shift consistency |
| Cost allocations | Equipment can be copied onto a journal line | No explicit allocation table/basis/version or unallocated remainder demonstrated |
| Payment | Separate partial-payment source | No immediate-paid combined mobile workflow demonstrated; API requires create then payment |
| Approval | Controller uses role checks | No expense-specific submit/approve/reject policy lifecycle demonstrated in inspected module |
| Dates | Expense date and payment date | Document/effective/accounting/due date separation not demonstrated |
| Attachments | Not present on Expense model/migration | Private source-linked evidence/document support not demonstrated |
| Currency/tax | Amount DECIMAL(15,2) | Currency, FX snapshot, tax components, and legal invoice compliance not demonstrated |
| Inventory | No inventory package found in audited backend application package tree | Inventory receipts/issues/costing must be separately audited; do not assume purchase/consumption support exists |
| Reports | Expense list response calculates paid/remaining | Aging, supplier statements, allocations, period reports, and full drill-down need audit |
| Period close | Design exists on separate docs branch | Backend enforcement for expense/payment dates must be confirmed before implementation |
| Offline | Architecture direction approved | Expense drafts/queue/retry/conflict behavior not demonstrated by this module |

## 4. Important implementation observations

1. The current API posts on expense creation. Introducing Draft/Approval states must preserve old API behavior or deliberately version/migrate it; do not silently change the meaning of existing clients.
2. The expense source is immutable after posting. Any richer editable draft workflow should use explicit draft state and a controlled posting transition, rather than weakening `V26`.
3. The current `Expense` constructor accepts both equipment and shift IDs, and the database tenant foreign keys validate ownership. The inspected service does not visibly verify that the selected shift belongs to the selected equipment; verify the shift/equipment relationship and add service/database tests if absent.
4. The current journal line carries an equipment dimension but no shift/project/site dimension. Adding operational dimensions to journal lines may be unnecessary if a separate analytical allocation ledger is designed correctly; avoid duplicating cost in the general ledger.
5. Expense payment locking is a useful concurrency foundation. The full test suite must establish whether simultaneous payment requests with different idempotency keys are correctly serialized and whether the payment sum uses the intended transaction isolation semantics.
6. Do not add a second supplier payable balance table if it can be derived/reconciled from journal balances and payment allocations. A supplier subledger may be needed, but it must reconcile to the control account.

## 5. Recommended target evolution

### First: preserve the existing accounting core
Keep `Expense`, `ExpensePayment`, `JournalPostingService`, chart accounts, and existing immutable journal protections as the baseline. No parallel ledger.

### Second: introduce a compatibility-safe source document model
Decide whether to extend the current expense header with lines or create a new versioned document/line aggregate. Preserve historical rows and old API contracts. Map one source document to exactly one expense recognition flow, even when it has multiple cost lines.

### Third: add supplier and settlement traceability
Only after auditing any existing supplier/customer master data. Link payments to payable source obligations; preserve partial settlement and journal reconciliation.

### Fourth: add cost allocations separately from journal recognition
Allocation rows distribute recognized source line costs to project/site/equipment/shift. They do not post new expense journals. Validate total allocation and expose unallocated remainder.

### Fifth: implement workflow/mobile/reporting
Add drafts, approval policy integration, immediate-paid UX, attachments, source-linked views, and reports only after backend state semantics are stable.

### Sixth: inventory integration separately
Audit and approve inventory costing/accounting policy before linking stock receipts/issues to expense documents.

## 6. Required follow-up audit checklist

- [ ] Inspect all expense-related migrations after V18, including trigger definitions and any schema changes.
- [ ] Inspect full expense concurrency tests and execute them against the intended MySQL version.
- [ ] Inspect `JournalPostingService` full transaction boundaries, reversal semantics, and liquidity-floor logic.
- [ ] Verify selected shift belongs to selected equipment when both are linked.
- [ ] Inspect accounting period-close backend enforcement and effective/posting date rules.
- [ ] Search for reusable supplier/customer entities and approval policy services.
- [ ] Inspect equipment, site, project, and shift ownership relationships.
- [ ] Confirm whether inventory is absent or implemented outside the scanned application package.
- [ ] Produce a compatibility map for old API/mobile clients.
- [ ] Define database constraints and exact migration/test order before implementation.

## 7. Safety

This audit is documentation-only and based on source inspection at the audited ref. It does not authorize schema changes, production migrations, PR merges, or changes to `main`. WF-08 manual Android acceptance remains a separate gate.
