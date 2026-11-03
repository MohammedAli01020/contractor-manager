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
| Dashboard | [~] | Refine and approve layout, metrics, and quick actions |
| Main navigation / More | [~] | Validate destination grouping and reachability |
| Shifts list | [~] | Prototype direction discussed; finish filters, statuses, and layouts |
| Create shift | [~] | Driver wage is variable per shift; define full form against backend |
| Shift details | [~] | Show operational, driver, equipment, and financial information |
| Completed shift correction | [~] | Dedicated correction flow; ordinary edit remains distinct |
| Equipment list/details | [~] | Equipment profile aggregates shifts, actual expenses, stock issues, drivers, and results |
| Driver account | [~] | Aggregate shift wages, advances, and payments into a statement |
| Advances | [~] | Separate driver receivable/advance workflow, never an operating expense by default |
| Expenses | [~] | One transaction can be linked to equipment and shift simultaneously; avoid duplicate posting |
| Custom categories | [~] | Tenant-specific labels/categories are configurable; accounting transaction types remain protected |
| Treasury and banks | [~] | Separate cash/bank accounts, receipts, payments, transfers, and statements |
| Inventory | [~] | Cover stock movement and direct issue without a store where supported |
| Reports | [ ] | Confirm report definitions against accounting backend |
| Platform Owner Web | [ ] | Separate responsive web design scope |

## Decisions recorded on 2026-11-03

- Driver wage is variable per shift. A suggested/default value may be offered, but the final wage is snapshotted on the shift and does not change when the driver's profile defaults change.
- A driver's account statement separates earned shift wages, advances, and actual payments. Partial payments leave the remaining balance outstanding.
- An advance is a separate movement against the driver's balance, not an operating expense by default.
- Users may add, rename, reorder, and deactivate tenant-specific categories. Core accounting transaction types remain protected.
- Used categories are deactivated rather than deleted; historical transactions retain their original meaning and audit trail.
- An expense may be associated with equipment, a shift, both, or neither. Linking the same expense to equipment and shift creates one financial transaction, not two.
- Equipment profile aggregates shifts, expenses, stock issues, drivers, and results. The shift remains the operational record; do not create duplicate standalone operation entries.
- Stock purchase and stock consumption are distinct. Direct purchase-and-use without a store should be supported where appropriate.
- Estimated equipment costs must not be posted as actual expenses or counted again when actual expenses already exist.
- Treasury accounts are maintained separately for each cashbox and bank account. A combined liquid-funds total may be supplementary, never a replacement for individual balances.
- Receipts and payments should link to the source receivable/payable/expense/wage where applicable. Paying a recorded obligation settles it; it does not create a second expense.
- Transfers between company-owned cash/bank accounts create paired account movements and are not revenue or expense.
- Opening balances use a controlled, auditable workflow; users cannot directly overwrite a posted account balance.

## Product and accounting guardrails

- A completed shift with a zero wage/fee must follow the backend's explicit business rule; the UI must not silently hide or invent a journal entry.
- Editing a completed shift's operational or financial values must use the approved correction flow when required, including reason/audit/version/idempotency behavior supported by the backend.
- Driver advances are not automatically operating expenses.
- Estimated equipment cost must not be posted as an actual expense.
- Cash and bank balances are not revenue or profit.
- Unpaid expenses and unpaid driver wages do not reduce cash/bank balances until actual payment.
- Transfers between company-owned accounts do not change profit.
- Financial records shown in multiple screens must never be duplicated in the ledger.
- Historical posted transactions must not be silently rewritten by changing a category label or default setting.
- Do not display prototype sample values as real customer data.

## Approval and implementation workflow

1. Build/revise the interactive prototype for one screen or flow.
2. Review it with the product owner.
3. Record explicit approval here and in that screen's specification.
4. Confirm relevant API/backend behavior and accounting posting rules.
5. Implement in Flutter in a separate, reviewed code change.
6. Test on the target Android device and update status only after verification.

## Change log

- 2026-11-03 — Initial design tracking created; ERP-inspired mobile direction selected; Design System v1.0 established.
- 2026-11-03 — Added driver account, configurable categories, expense linking, equipment profile, and inventory workflow decisions.
- 2026-11-03 — Added treasury and bank account specification covering balances, receipts, payments, transfers, opening balances, and reconciliation boundaries.
