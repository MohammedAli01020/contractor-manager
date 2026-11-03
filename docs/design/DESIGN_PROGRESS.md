# Design Progress — Contractor Manager

Last updated: 2026-11-03  
Current design baseline: [Design System v1.0](design-system/v1.0.md)

## Status legend

- [ ] Not started
- [~] Draft / in review
- [x] Product direction/decision approved
- [>] Implemented and verified

## Shared foundation

| Item | Status | Notes |
|---|---|---|
| ERP-inspired visual direction | [x] | Selected by product owner |
| Arabic-first / RTL | [x] | Required across contractor mobile screens |
| Color direction | [x] | Navy, white, light blue, semantic status colors |
| Typography | [~] | Cairo preferred; validate actual Flutter rendering |
| Navigation architecture | [~] | Proposed five primary destinations; needs screen-level review |
| Design System v1.0 documentation | [x] | Initial shared baseline |

## Screens and workflows

| Screen / flow | Status | Next action |
|---|---|---|
| Dashboard and reports | [~] | Review definitions, period filters, metrics, and drill-down |
| Main navigation / More | [~] | Validate destination grouping and reachability |
| Shifts list | [~] | Finish filters, statuses, and layouts |
| Create shift | [~] | Driver wage is variable per shift; define full form against backend |
| Shift details | [~] | Show operational, driver, equipment, and financial information |
| Completed shift correction | [~] | Dedicated correction flow; ordinary edit remains distinct |
| Equipment list/details | [~] | Aggregate shifts, actual expenses, stock issues, drivers, and results |
| Driver account | [~] | Aggregate shift wages, advances, and payments into a statement |
| Advances | [~] | Separate driver receivable/advance workflow |
| Expenses | [~] | One transaction can be linked to equipment and shift simultaneously |
| Custom categories | [~] | Tenant-specific labels/categories; protected accounting transaction types |
| Treasury and banks | [~] | Separate accounts, receipts, payments, transfers, and statements |
| Inventory and warehouses | [~] | Multiple warehouses, receipts, issues, transfers, stocktake, and direct purchase/use |
| Monthly and annual close | [~] | Review close states, checklist, reconciliation, corrections, approvals, and year-end policy |
| Platform Owner Web | [ ] | Separate responsive web design scope |

## Decisions recorded on 2026-11-03

- Driver wage is variable per shift. A suggested/default value may be offered, but the final wage is snapshotted on the shift.
- Driver statements separate earned wages, advances, and actual payments. Partial payments leave the remaining balance outstanding.
- Advances are separate movements against the driver's balance, not operating expenses by default.
- Users may add, rename, reorder, and deactivate tenant-specific categories. Core accounting transaction types remain protected.
- Used categories are deactivated rather than deleted; historical transactions retain their original meaning and audit trail.
- An expense may be associated with equipment, a shift, both, or neither. One source expense linked to both remains one financial transaction.
- Equipment profile aggregates shifts, expenses, stock issues, drivers, and results. The shift remains the operational record.
- Stock purchase/receipt and stock consumption/issue are distinct. Direct purchase-and-use without a warehouse should be supported.
- Multiple warehouses are supported from the initial product model; one default warehouse is sufficient for a new/small contractor.
- Stock quantities are maintained per item per warehouse. Internal transfers change location, not company profit.
- Stocktake differences require a reason and auditable adjustment; users must not overwrite balances directly.
- Negative stock is blocked by default.
- Inventory costing method is not selected in this design draft; it must be explicit and match backend/accounting policy before implementation.
- Estimated equipment costs are not actual posted expenses.
- Treasury accounts have separate derived balances. Receipts/payments link to source obligations; internal cash/bank transfers are not income or expense.
- Opening balances and posted financial corrections use controlled, auditable workflows.
- Management reports must distinguish period profitability, actual cash flow, point-in-time cash/bank balances, receivables, payables, and inventory value.
- Profit-and-loss, cash-flow, receivable/payable, driver, equipment, inventory, trial-balance, and general-ledger reports must reconcile to their source records.
- Report totals must drill down to source transactions; repeated references to one source transaction must not double-count it.
- Equipment profitability is not claimed unless revenue attribution and actual cost allocation are explicit and sufficiently complete.
- Inventory valuation is displayed only after the costing method and cost completeness are verified.
- Monthly close is a controlled review/approval workflow with explicit period boundaries, blocking exceptions, reproducible close evidence, and an auditable reopen path.
- Closed periods reject ordinary edits, deletion, and backdated posting. The default correction path posts in the first open period while preserving both effective and posting dates; authorized restatement requires a reasoned reopen.
- Annual close reuses monthly controls; year-end profit/equity transfer accounts and local tax treatment remain subject to accounting-policy confirmation.

## Product and accounting guardrails

- Completed zero-wage/zero-fee shifts follow explicit backend rules; UI must not invent journal entries.
- Completed shift corrections use the approved correction/audit workflow.
- Driver advances are not automatically operating expenses.
- Estimated costs must not be posted as actual expenses.
- Cash and bank balances are not revenue or profit.
- Unpaid expenses and wages do not reduce cash/bank until paid.
- Internal account/warehouse transfers do not change profit.
- Records shown in multiple screens must never be duplicated in the ledger or stock movement history.
- Historical posted transactions and stock movements must not be silently rewritten.
- Report totals must be derived from the accounting/operational source of truth and must indicate incomplete data.
- Period close/reopen and year-end posting must be enforced by backend/database controls, not only by Flutter.
- Close operations must be transactional, tenant-scoped, idempotent, and safe under concurrent requests and multiple application instances.
- Prototype sample values are never presented as real customer data.

## Approval and implementation workflow

1. Build/revise the interactive prototype for one screen or flow.
2. Review it with the product owner.
3. Record explicit approval here and in that screen's specification.
4. Confirm relevant API/backend behavior and accounting posting/costing rules.
5. Implement in Flutter in a separate, reviewed code change.
6. Test on the target Android device and update status only after verification.

## Change log

- 2026-11-03 — Initial design tracking created; ERP-inspired, Arabic-first mobile direction and Design System v1.0 established.
- 2026-11-03 — Added driver account, configurable categories, expense linking, equipment profile, and inventory workflow decisions.
- 2026-11-03 — Added treasury and bank account specification.
- 2026-11-03 — Added multiple-warehouse inventory specification, including stock receipts/issues/transfers, stocktake, direct purchase-and-use, and costing-policy guardrails.
- 2026-11-03 — Added management dashboard and reports specification with source drill-down and accounting reconciliation guardrails.
- 2026-11-03 — Added monthly/annual period-close draft covering review workflow, reconciliation, close exceptions, corrections, reopen controls, annual close, mobile UX, and concurrency/tenant-integrity requirements.
