# Expense Foundation — Product and Accounting Specification

**Status:** Product-owner direction approved on 2026-11-06; implementation specification  
**Workstream:** Expense foundation (design/documentation only)  
**Base:** `integration/stabilization-2026-11`  
**Scope:** Contractor mobile app, backend accounting, treasury, inventory, cost analytics

## 1. Product objective

Provide a fast mobile-first expense workflow for small contractors while preserving accounting integrity, auditability, tenant isolation, and reliable cost reporting across projects, work sites, equipment, and shifts.

The expense module is not a standalone list of amounts. It is the source workflow that connects supplier obligations, accounting recognition, payments, inventory, and analytical cost attribution without duplicating financial events.

## 2. Non-negotiable principles

1. One business expense/source document has one stable source identity and one authoritative posting flow.
2. Linking or displaying a source transaction from multiple screens never creates another journal entry or another expense.
3. Accounting recognition, payment/cash movement, inventory movement, and analytical allocation are separate concepts and states.
4. Drafts may be edited according to permission. Posted financial records are immutable; corrections use controlled reversals/adjustments with reasons and audit evidence.
5. A payment settles an existing payable; it does not recognize the expense a second time.
6. A stock purchase is not automatically an operating expense. Stock receipt and stock consumption are distinct events.
7. Estimates are planning data only and never enter actual posted expense totals.
8. Tenant-specific categories are labels. They cannot redefine protected accounting transaction types or posting behavior.
9. All write operations are tenant-scoped, transactional, idempotent, and safe under concurrent requests.
10. The server is authoritative for approvals, posting, balances, inventory, and financial status.

## 3. Expense capture and source record

### 3.1 Header fields

- Tenant/company (derived from authenticated context; never trusted from a client-supplied tenant ID).
- Unique expense/document number generated safely within the tenant and configured numbering scope.
- Supplier/payee, where applicable.
- Document/reference number (optional, subject to uniqueness policy).
- Document date, accounting/effective date, due date, and creation timestamp as distinct values.
- Transaction currency and amount; tenant base-currency amount and exchange-rate snapshot when foreign currency is enabled.
- Description/notes.
- Category and optional subcategory.
- Attachments/evidence with private tenant-scoped access.
- Branch, project, and work site dimensions where applicable.
- Creator, submitter, approver, posting actor/process, and timestamps.

### 3.2 Expense lines

Use line-level amounts and classifications rather than assuming every supplier document has one homogeneous purpose. A document may contain multiple expense lines and, when supported by inventory, stock-purchase lines. Each line has quantity/unit where relevant, net amount, tax treatment, gross amount, category/item, and accounting classification selected by controlled configuration.

The document total must reconcile exactly to its lines, currency precision, discounts, and tax components. Do not infer tax rates or legal invoice compliance from a category name. Egyptian tax/e-invoice requirements and tenant tax configuration require separate accounting/legal validation before claiming compliance.

### 3.3 Operational association

A source expense/line may be associated with:
- Company-wide overhead (no operational target).
- Project and/or work site.
- Equipment.
- Shift.
- Equipment and shift together.
- Multiple cost targets through explicit cost allocations.

When both equipment and shift are selected, validate that the shift belongs to the selected equipment (or reject the inconsistent association). The equipment and shift screens reference the same source identity; they do not own duplicate expense records.

Do not force an equipment or shift selection for general overhead.

## 4. Lifecycle and state separation

Keep these states distinct; do not collapse them into one generic status:

### 4.1 Business/approval lifecycle

- Draft
- Submitted
- Pending approval (only when policy requires it)
- Approved / Rejected / Returned for changes
- Cancelled, where cancellation is allowed

### 4.2 Accounting lifecycle

- Not posted
- Posted
- Reversed / adjusted through a linked correction

Approval is not posting. A submitted or approved expense must not affect the general ledger until the posting action succeeds.

### 4.3 Settlement lifecycle

- Unpaid
- Partially paid
- Paid
- Overdue (derived from due date and outstanding amount, not manually editable)

Settlement is derived from linked payment allocations and outstanding payable. A status field must not be independently edited to claim payment.

### 4.4 Correction lifecycle

Draft edits are allowed within permission and validation rules. After posting, no ordinary update/delete is permitted. A correction creates linked reversal/adjustment evidence, preserves original and corrected values, requires a reason, respects period-close rules, and is idempotent.

## 5. Accounting and posting rules

### 5.1 Unpaid supplier expense

At successful posting, for a genuine period expense:

- Debit: configured expense account (or other appropriate asset/cost account).
- Credit: supplier/accounts-payable control account.

### 5.2 Paid immediately

When the expense is paid immediately, record one coherent source expense plus its payment settlement:

- Debit: configured expense/asset account.
- Credit: selected cashbox/bank account.

If the architecture first recognizes a payable and then records a payment, the two journal events must be linked and committed atomically when the user chooses a single immediate-payment workflow. Either implementation must reconcile to one expense recognition and one cash movement, never two expense recognitions.

### 5.3 Partial/subsequent payment

Payment posting debits the payable and credits the selected cash/bank account. It reduces outstanding liability only. It must not debit the expense account again.

Support multiple partial payments, each with its own payment identity, date, source account, reference, actor, and audit evidence. Outstanding amount is derived from posted payment allocations.

### 5.4 Supplier refunds and credit notes

Do not accept negative expense amounts as a shortcut. Use a linked supplier credit note/refund or controlled reversal/adjustment that references the original source, preserves dates and audit history, and applies to the payable/cash/bank according to the actual settlement.

### 5.5 Cash and bank

- Cash/bank balance changes only through posted receipts, payments, transfers, and approved opening-balance entries.
- An unpaid expense does not reduce cash/bank.
- Paying an expense settles its payable and reduces cash/bank; it does not create a second expense.
- Internal transfers are not income or expense.

## 6. Inventory boundary

1. Purchase for stock creates a supplier obligation/payment and inventory receipt/asset recognition according to the selected accounting policy; it is not immediately an operating expense merely because money was paid.
2. Issue/consumption creates a stock movement and recognizes the consumed cost according to the approved costing method.
3. The same purchased item must not be expensed in full at purchase and expensed again at issue.
4. Direct purchase-and-use without a warehouse is supported as a distinct path. It recognizes the appropriate cost once and records evidence of direct consumption; it must not create a fictitious stock balance.
5. Mixed supplier documents may split expense lines and stock lines while retaining one source document and reconciled totals.
6. Multiple warehouses remain supported in the product model; a small tenant may start with one default warehouse.
7. Negative stock is blocked by default. Stocktake differences require reasoned, auditable adjustments.
8. Inventory costing method (for example, weighted average or FIFO) is intentionally not selected by this document. Do not implement inventory valuation or claim valuation reports until the accounting policy, precision, returns, transfers, and concurrency behavior are explicitly approved and tested.

## 7. Cost allocation and operational profitability

### 7.1 Separate source transaction from allocations

Cost allocation is an analytical distribution of an already-recognized source cost. It is not a new expense and must not create a second journal entry.

Support allocation targets such as project, site, equipment, and shift. A line may be:
- Directly assigned to one target.
- Split across multiple targets using explicit amounts or percentages.
- Left unallocated as company/period overhead.

### 7.2 Allocation integrity

- Store allocation basis, target identity, amount/percentage, currency/base-currency amount, actor, timestamp, and reason/version.
- Validate that allocations do not exceed the allocatable source line amount.
- Require full allocation only when a report or workflow explicitly claims fully allocated operational cost; otherwise expose the unallocated remainder.
- Reallocation after posting is an audited analytical correction/version, not a new expense posting.
- Reports must count the source line once at company level and use allocation rows only to distribute its value across dimensions.
- Never sum both the original line and all its allocation rows in the same total.

### 7.3 Equipment and shift cost

Equipment/shift cost reports may include direct expense allocations, consumed inventory cost, and worker wages as separate cost components. Do not add estimated fuel/maintenance cost to actual expenses. A shift's wage is already recognized through the shift compensation workflow; expense reports must not recreate wage journals.

Do not label equipment/shift results as final profitability if revenue attribution, cost allocation, or inventory costing is incomplete. Show an explicit incomplete/unallocated indicator and drill down to source records.

## 8. Configurable categories and protected accounting configuration

Tenant users with permission may create, rename, reorder, and deactivate categories. Categories:
- Are descriptive labels, not journal transaction types.
- May have a controlled account mapping only through elevated permission and audited configuration.
- Cannot turn advances into expenses, payments into income, or stock movements into expense recognition.
- Cannot be hard-deleted when referenced by historical records.
- Must preserve historical category identity/meaning; renaming a label must not rewrite historical accounting postings.

Core account types, control accounts, journal rules, and posting semantics remain system-controlled.

## 9. Approvals, permissions, and audit

Use the approved role/permission and approval-policy foundation:
- Separate action permission from approval requirement.
- Configure expense approval by tenant, operation, amount/currency, and resource scope.
- Keep maker-checker separation for sensitive approvals by default.
- The requester cannot approve their own request unless a specifically authorized policy permits it.
- Approval does not execute payment or post a journal.
- Recheck authorization at submit, approve/reject, execute/pay, post, correct, and export.
- Record immutable audit evidence: source identity, tenant, actor, event, timestamp, before/after values, reason, policy/version, approval decision, posting/journal references, and payment references.

Example monetary thresholds are tenant configuration, not universal defaults.

## 10. Period close and dates

- Preserve document/effective date separately from accounting posting date.
- Closed periods reject ordinary edits, deletion, and backdated posting.
- Default correction path posts in the first open period while preserving the original effective date and actual posting date.
- Restatement requires authorized, reasoned period reopen and immutable evidence.
- Expense, payment, and allocation corrections must follow the same close controls and never silently rewrite historical ledgers.

## 11. Mobile-first UX

The contractor's primary workflow is mobile. The initial UX should optimize:
- Fast add expense with sensible defaults and progressive disclosure.
- Category, amount, date, description, and attachment first; optional supplier/project/equipment/shift details revealed as needed.
- Search/select equipment and shift with clear consistency validation.
- Clear paid-now vs unpaid choice; partial payment handled in a linked settlement step.
- Review summary before submit/post, including the source, allocation, payment account, and expected effect.
- Expense details show source document, approval, posting, payment history, allocations, attachments, and audit timeline.
- Equipment/shift screens show a linked expense preview and open the same source detail.
- Arabic-first RTL, accessible touch targets, validation and useful empty/error states.

### Offline-first safety

Offline-first is the approved architecture direction, but financial completion is server-confirmed:
- Offline may save a local draft and queue eligible non-posting metadata actions.
- Do not show a payment, approval, posting, balance update, stock issue, or correction as completed before server confirmation.
- Use stable idempotency keys for retries.
- Conflicts on posted/closed financial records require server reconciliation and user review; never silently overwrite.

## 12. Reporting requirements

Provide distinct reports and drill-downs for:
- Expense recognition by accounting period/category/supplier/project/site.
- Paid vs unpaid and aging of supplier payables.
- Cash/bank payment flow, separately from expense recognition.
- Actual cost by equipment and shift based on explicit allocations.
- Inventory purchases/receipts vs issues/consumption and inventory valuation only after costing policy approval.
- Unallocated overhead and incomplete cost attribution.
- General ledger and trial balance reconciliation.

All totals must reconcile to journal/source records. One source transaction shown in multiple places is counted once in each relevant aggregate. Reports must disclose incomplete allocation/costing data.

## 13. Security, tenancy, and reliability

- Every query and mutation is tenant-scoped on the server.
- Client-supplied tenant IDs, account IDs, or allocation targets are not trusted without server-side ownership/scope validation.
- Use database constraints and transactional service boundaries for source, lines, approval, posting, settlement, and allocations.
- Enforce idempotency for create/submit/post/pay/correct operations.
- Protect against duplicate posting under concurrent requests and multiple API instances.
- Posted journal entries and audit records are immutable; corrections use reversal/adjustment.
- Use optimistic version checks for editable drafts and controlled correction workflows.
- Attachments are private, access-controlled, and referenced from the source record.
- No production migration without tested backup/restore and explicit release authorization.

## 14. Required test matrix before release

- Tenant isolation for expense, supplier, category, account, equipment, shift, project, allocation, attachment, and payment.
- One expense linked to equipment and shift creates one source identity and one posting flow.
- Repeated requests with same idempotency key do not duplicate expense, journal, payment, or allocation.
- Concurrent submissions/postings/payments cannot overstate amounts or balances.
- Unpaid expense posts payable but does not change cash/bank.
- Immediate payment records expense recognition and cash movement exactly once.
- Partial payments reduce payable and cash/bank correctly without re-expensing.
- Paying an already-posted expense never creates another expense debit.
- Credit note/refund and reversal preserve source links and period dates.
- Mixed expense/inventory lines reconcile to the source document.
- Stock purchase and stock consumption are not double-counted.
- Allocation sums, unallocated remainder, and multi-target splits reconcile exactly.
- Reallocation changes analytical reports without creating a second expense journal.
- Category rename/deactivation preserves historical meaning.
- Approval, rejection, self-approval, cancellation, and execution/posting separation follow policy.
- Closed-period edits/backdating are rejected; approved reopen/correction is auditable.
- Currency precision and rounding reconcile across lines, allocations, journals, and payments.
- Flutter widget/API tests, backend integration tests, migration tests, and real Android acceptance are required.

## 15. Delivery plan and gates

### EXP-01 — Domain and accounting audit
Map the current backend entities, journal services, expense endpoints, treasury/payment model, inventory model, tenant authorization, idempotency, and period-close rules. Reuse existing mechanisms; do not introduce parallel ledgers or duplicate account balances.

### EXP-02 — Source expense and categories
Implement the canonical expense source, line model, configurable categories, draft lifecycle, tenant validation, attachments, and audit trail. No payment or stock valuation shortcuts.

### EXP-03 — Posting and supplier settlement
Implement approved expense posting, payable balance, paid-now and partial/subsequent payment flows, idempotency, and concurrency tests.

### EXP-04 — Operational cost allocation
Implement explicit allocations to project/site/equipment/shift, unallocated remainder, reconciliation, and drill-down without duplicate journals.

### EXP-05 — Inventory integration
Only after the inventory costing method and stock accounting policy are approved. Integrate receipts/issues/direct purchase-use without duplicate cost recognition.

### EXP-06 — Flutter mobile workflow and reports
Implement Arabic-first screens, role-aware permissions, approval states, source-linked equipment/shift views, reports, and real-device tests.

### Release gate
Do not mark the module sellable until accounting reconciliation, tenant isolation, concurrency/idempotency, period-close behavior, Android acceptance, backup/restore rehearsal, and user-facing documentation pass.

## 16. Explicitly unresolved policy decisions

These items must be resolved before the corresponding implementation; they are not to be guessed:
- Inventory costing method and treatment of returns, freight, and landed cost.
- Egyptian tax/VAT/e-invoice legal requirements and whether the product issues tax invoices (separate from ordinary expense evidence).
- Foreign-currency activation, approved exchange-rate source, and realized/unrealized FX policy.
- Supplier master-data requirements and duplicate supplier matching.
- Exact account mapping defaults for expense categories and stock items.
- Whether cost allocation is editable after posting by an authorized analytical adjustment and how reports version it.

## 17. Product-owner decision record — 2026-11-06

The product owner authorized proceeding with the professional SaaS expense foundation. This specification establishes the product and accounting direction. It does not authorize production migrations, PR merges, or bypassing the existing WF-08 acceptance gate.

The core approved rule is: **one source expense, one accounting recognition, separately linked payments and stock movements, and analytical allocations that never create duplicate expense postings.**

