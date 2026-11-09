# ADR — Configurable Workforce Financial Items

- **Status:** Partially implemented on working branch; remaining application and posting decisions require explicit product approval
- **Date:** 2026-11-09
- **Working branch:** `feature/workforce-ledger-unification`
- **Scope:** Tenant-configurable compensation components and workforce-related financial items
- **Depends on:** Canonical Workforce identity, compensation agreements, Workforce ledger, double-entry posting foundation

## 1. Problem

A contractor SaaS must support different compensation policies without requiring a code change for every customer. One company may use transport allowance, meal allowance, remote-site allowance, production bonus, absence deduction, or a custom item that another company does not use.

The existing code already has compensation components attached to individual compensation agreements, a Driver-keyed worker ledger with several controlled movement types, and an expense record carrying a free-form `category_code`. These are useful foundations, but they are not a reusable tenant-owned Workforce financial-item catalog. In particular, the expense `category_code` is stored as text on each expense; the inspected entity/schema do not establish a dedicated tenant category-definition table.

This ADR defines the product/domain model. On the working branch, the tenant-owned definition catalog (V60) and optional compensation-component link (V61) are implemented with backend CRUD and integration coverage. The per-person application lifecycle, approvals, financial posting, and statement projection are not implemented; the ADR does not authorize those steps or production rollout.

## 2. Decision proposed

Build a configurable financial-item catalog owned by each tenant and shared by that tenant's Workforce workflows.

A financial item definition describes a reusable policy. A financial transaction records one actual occurrence. A compensation agreement may reference eligible recurring items, while one-off adjustments may reference the same catalog without changing the underlying agreement.

The system must distinguish the following layers:

1. **Item definition:** what the tenant calls the item and its business meaning.
2. **Calculation rule:** how an amount is calculated, if calculation is applicable.
3. **Application/approval:** who receives it, when it applies, and who approved it.
4. **Posted financial movement:** the immutable, amount-specific result linked to the Workforce member and, where required, a journal entry.

Do not encode tenant-defined item names as Java enums or hard-coded database transaction types.

## 3. Item families and financial semantics

A configurable label must not allow a tenant to change the accounting meaning of a movement arbitrarily. Each definition belongs to a controlled system family:

| Family | Examples | Balance/accounting behavior |
|---|---|---|
| Earnings component | Base wage, transport allowance, meal allowance, remote-site allowance, production bonus | Increases earned/payable compensation |
| Deduction component | Absence deduction, lateness penalty, approved damage deduction | Reduces payable only under an authorized policy and approval |
| Earned-pay advance | Advance against an already-earned amount | Reduces outstanding payable; cannot exceed eligible earned balance |
| Recoverable advance | Cash advanced to be recovered from future earnings | Creates a separate recoverable asset; does not reduce earned payable at issuance |
| Payment | Cash/bank payment to the person | Settles payable; not a new expense or earning |
| Custody/asset accountability | Equipment, tools, fuel card, or other entrusted property | Separate custody/settlement lifecycle; not an automatic wage deduction |
| Reimbursement | Approved employee-paid business expense | Reimbursement liability/payment flow; not compensation by default |
| Settlement/adjustment | Approved balance settlement or correction | Controlled, reasoned, auditable workflow with explicit posting rules |

A tenant may customize names, descriptions, visibility, and allowed calculation parameters within the selected family. A tenant may not turn a custody item into a wage deduction merely by changing a setting. High-risk semantics remain controlled by the application.

## 4. Definition fields

The future item definition should support, subject to detailed schema review:

- Tenant ownership and stable internal ID/code.
- Display name, description, and optional category/group.
- Controlled family/type and direction/effect.
- Active/inactive state and effective dates.
- Whether it is available for recurring compensation agreements, one-off entries, or both.
- Allowed calculation methods and required inputs.
- Default currency and precision policy, where applicable.
- Approval requirement and eligible roles/permissions.
- Optional links to sites, projects, shifts, equipment, or work periods.
- Accounting mapping selected from permitted tenant chart-of-account options, validated by the backend.
- Creation/update actor and timestamps, plus audit history.

Codes are stable identifiers; display names may be changed. Deactivation prevents new use but does not erase history. Referenced definitions must not be hard-deleted.

## 5. Calculation methods

Support a deliberately bounded set of calculation methods rather than arbitrary formulas or executable expressions:

- Fixed amount per application.
- Percentage of a defined, approved base.
- Per hour.
- Per day.
- Per completed shift.
- Per quantity/unit.
- Manually entered amount with permission and reason.

Each method must define its unit, base, rounding rule, effective date, caps/floors where applicable, and behavior when required source data is missing. Do not allow a percentage rule to choose an ambiguous base. Do not calculate from client-submitted totals when the server can derive the amount from approved source records.

Arbitrary scripts, SQL expressions, or user-supplied executable formulas are out of scope.

## 6. Recurring versus one-off application

- **Recurring agreement component:** effective-dated rule attached to a Workforce member's compensation agreement. It applies only for the agreed period and eligible work facts.
- **One-off item:** an individually requested item with date, amount/input, reason, and approval as required.
- **Operationally derived item:** calculated from approved attendance, shift, hours, days, or quantity records, with a traceable source.

Do not silently recalculate already-approved or posted periods when an item definition or agreement changes. New definitions/rules affect only their effective period and future applications unless an explicit, controlled adjustment is created.

## 7. Posting and historical snapshots

When an item is applied, persist an immutable snapshot sufficient to explain the result later:

- Workforce member and tenant.
- Definition ID plus item code/name/family snapshot.
- Calculation method and relevant rule/version snapshot.
- Input quantities, base amount, rate/percentage, and final amount.
- Currency, rounding result, effective date and accounting date.
- Source shift/attendance/agreement/document references as applicable.
- Reason, creator, approver, approval timestamp, and status.
- Journal link and idempotency/source reference when posted.

Changing a definition, rate, display name, or account mapping must not mutate an existing applied or posted movement. Posted movements and journals are immutable; corrections append linked reversal/adjustment movements with reason and authorization.

## 8. Approval, permissions, and tenant safety

- Every definition, application, approval, and query is tenant-scoped from authenticated server context.
- Backend authorization is mandatory; hiding a mobile control is not security.
- Separate permissions for defining items, applying one-off items, approving deductions, approving payments, and viewing sensitive financial details.
- The creator must not approve their own high-risk deduction or adjustment where the tenant's approval policy requires separation of duties.
- Require a reason and audit event for manual amounts, deductions, reversals, and exceptional overrides.
- Validate the item family, account mapping, source references, and amount on the server.
- Use idempotency and transaction-level concurrency control for every operation that can change a balance or create a journal.
- Enforce tenant-safe foreign keys and uniqueness constraints in MySQL where practical.

## 9. Mobile-first product experience

The contractor's everyday workflows are primarily mobile. The Workforce member profile should expose a clear financial summary and statement, while keeping actions understandable:

- Earnings and approved additions.
- Advances (earned-pay and recoverable shown separately).
- Payments.
- Deductions.
- Custody and reimbursements in their own sections.
- Pending approvals and period settlements.

A tenant administrator configures the item catalog in an appropriate settings workflow. A field supervisor should only see and apply items allowed by their role. Avoid exposing a large accounting configuration form to ordinary mobile users.

The design should remain compatible with a later offline-first phase: stable IDs, explicit pending/approved/posted states, idempotency keys, immutable server-authoritative posting, and conflict-aware synchronization. Offline support itself is not part of this ADR's implementation scope.

## 10. Reporting

Provide per-person statement and tenant reports that distinguish:

- Gross earned compensation.
- Additions/allowances/bonuses.
- Deductions.
- Earned-pay advances.
- Recoverable advance outstanding.
- Payments and remaining payable.
- Reimbursements.
- Custody balances and settlements.

Do not present all movement totals as one undifferentiated "deductions" or "expenses" figure. Reports must reconcile to the ledger and relevant control accounts, and must preserve the item snapshot used at posting time.

## 11. Delivery sequence

1. Review and explicitly approve the domain taxonomy and invariants in this ADR.
2. Inventory current ledger movement types, compensation agreement structures, expense categories, account mappings, and tenant usage. Identify overlap without conflating expense categories with Workforce items.
3. Produce exact schema/API contracts and tests for the application lifecycle before implementation.
4. Separately design and approve the canonical Workforce ledger writer transition before any posting endpoint or financial mutation.
5. Reconcile historical data, backups, rollback, and production rollout before any legacy-table or trigger removal.d a migration/compatibility plan; review the current Workforce-ledger writer transition separately.
4. Implement item definitions and validation without changing the active financial writer.
5. Implement calculation/application and approval workflows with unit, MySQL integration, tenant-isolation, idempotency, and concurrency tests.
6. Integrate approved applications with the canonical Workforce ledger and double-entry posting in one transaction, only after the writer/locking design is approved.
7. Deliver mobile profile/statement and tenant configuration UI.
8. Add reconciliation/reporting and migration evidence before retiring any legacy path.

Do not introduce a second authoritative ledger or dual-write. The financial-item engine must use the canonical ledger posting boundary once that transition is safely completed.

## 12. Explicit non-authorizations

This proposal does not authorize:
- Flyway migrations or database schema changes.
- New production financial writers or dual-write.
- Changes to Driver-ledger writer, V56 mirror trigger, or correction triggers.
- Historical backfill or recalculation.
- Changes to posted journals or historical movements.
- Deletion of legacy tables, columns, or triggers.
- Production deployment, data access, PR merge, or changes to `main`.

Those actions require separate implementation review, tests, and explicit approval at the relevant cutover boundary.

## 13. Open decisions for product approval

1. Which item families should be available in the first sellable release, and which should remain later phases?
2. Should each tenant be able to choose its own approval thresholds and approval chains, within platform-enforced safety limits?
3. Which accounting mappings should be tenant-selectable versus system-managed?
4. Should recurring components be applied per shift/pay period, or should the first release support only shift-level and one-off application?
5. What local payroll/legal rules need review by a qualified Egyptian accountant before deductions, penalties, or payroll exports are enabled?

**Approval rule:** This ADR remains proposed until the product owner explicitly approves it. No implementation is inferred from discussion alone.


## 14. Repository inventory — source-level findings (2026-11-09)

Reviewed the current branch source and migrations at the time of this update. This is a code/schema inventory, not a claim that every tenant's live data has been inspected.

### 14.1 Existing compensation agreements and components

- `compensation_agreements` are tenant-scoped and Workforce-member keyed. They have effective dates, currency, source, approval metadata, replacement linkage, and optional assignment scope.
- `compensation_components` belong to one agreement; they are not currently reusable catalog definitions shared across agreements.
- Each component has `component_code`, `display_name`, `basis`, `rate`, `unit_code`, optional `shift_type`, and ordering.
- Existing basis enum values are `SHIFT`, `HOUR`, `DAY`, `QUANTITY`, and `FIXED`.
- Components are created inside a compensation-agreement draft and the agreement must be approved before use. The controller exposes list/create-draft/approve/approve-replacement/cancel operations; it does not expose a tenant-wide item catalog.
- `ShiftCompensationResolver` currently resolves one matching SHIFT, HOUR, or QUANTITY component for a shift. Although DAY and FIXED are valid component basis values in the entity/schema, the inspected shift resolver does not apply them. This is a functional coverage gap to address in a separately designed compensation-resolution phase, not by silently assuming these bases already work.
- The resolver freezes component/agreement identity, code, display name, basis, unit, rate, and amount on the shift snapshot. This is a useful historical-snapshot pattern to preserve.

### 14.2 Existing worker ledger

The active application writer is still `DriverLedgerService`, persisting `driver_ledger_entries`. The inspected service confirms these behaviors:

- `PAYMENT`: reduces earned payable and posts a journal against the worker-payable account and selected cash/bank account.
- `ADVANCE` (request mode `AGAINST_EARNED`): reduces earned payable and is rejected if it exceeds available payable.
- `RECOVERABLE_ADVANCE` (request mode `RECOVERABLE`): posts to the recoverable-worker-advances asset account and has zero effect on earned payable.
- `EARNING`: created by shift completion through the shift financial workflow.
- `ADVANCE_RECOVERY`: exists in the database movement taxonomy for recoverable advance recovery.
- `ADJUSTMENT`: exists in the database movement taxonomy, but must not be treated as an unrestricted generic custom item.
- Correction reversal movements are part of the later correction migrations/workflow and must remain append-only and linked to their source correction/journal.

The ledger currently has a controlled transaction-type column and signed `balance_effect`; it does not store a reusable financial-item definition ID, item-rule snapshot, approval workflow for arbitrary additions/deductions, or tenant-defined display taxonomy. V56 mirrors the legacy Driver writer into `workforce_ledger_entries`; that mirror is not a Workforce-native writer.

### 14.3 Existing expenses are not Workforce items

- `expenses.category_code` is a required text field (40 chars) with a nonblank check; the inspected schema does not establish a foreign key to a tenant-owned category-definition table.
- Expense creation selects a tenant-owned EXPENSE chart account, creates an expense source record, and posts a journal in one transaction.
- Expense payment is a separate source/payment workflow with a selected cash/bank account and its own journal.
- This is a company-expense/supplier-payable workflow. It must not be reused as the worker's compensation-item engine or confused with a reimbursement workflow.

### 14.4 Gap matrix

| Capability | Current source evidence | Assessment |
|---|---|---|
| Workforce identity as agreement owner | Agreement uses tenant + Workforce member | Present |
| Effective-dated and approved compensation agreement | Agreement lifecycle and replacement service | Present |
| Agreement-scoped compensation components | Component entity and draft request | Present |
| Reusable tenant-wide item catalog | No catalog entity/API found in reviewed paths | Missing |
| Tenant-defined additions and deductions as one-off worker items | No dedicated definition/application flow found in reviewed paths | Missing |
| Controlled advance/payment semantics | Driver ledger service + journals | Present, still Driver-ledger writer |
| Recoverable advance balance separate from payable | Separate account and zero payable effect | Present |
| Generic expense category master | Expense stores free-form category code in inspected schema | Not established |
| Custody/asset accountability lifecycle | No dedicated Workforce custody model identified in this focused inventory | Not established |
| Reimbursement lifecycle for Workforce members | No dedicated reimbursement model identified in this focused inventory | Not established |
| Component snapshot on shift | Shift compensation snapshot resolver | Present for supported resolver bases |
| DAY/FIXED shift-resolution behavior | Enum/schema accept values; inspected resolver does not apply them | Gap |
| Approval and audit for arbitrary one-off additions/deductions | No dedicated workflow identified | Missing |

### 14.5 Consequences for the implementation design

1. Do not replace `CompensationComponent` with the new catalog. Keep agreement-specific component terms as the effective-dated contractual configuration, then let them reference a reusable tenant catalog definition where appropriate.
2. Do not put arbitrary tenant labels into `transaction_type`. Keep the ledger's canonical movement families controlled, and attach a catalog item reference plus immutable item/rule snapshot to the movement/application record.
3. Do not route reimbursements, custody, earned-pay advances, or recoverable advances through a generic addition/deduction endpoint. They have distinct liabilities/assets and lifecycle rules.
4. Do not infer that free-form `category_code` on expenses is a configurable category master. A separate expense-category project, if desired, should remain distinct from Workforce item definitions.
5. Before any schema proposal is finalized, inspect every current writer/reader and all tenant data for actual movement types, journal-account mappings, corrections, and compensation component usage. The source-level findings above do not replace that inventory.
6. First implementation unit after product approval should be a catalog/domain contract and exact schema/API design review. It must not change the active ledger writer or create new financial postings until the coordinated Workforce-ledger writer transition is separately approved and tested.
