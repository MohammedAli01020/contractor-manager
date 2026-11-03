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
| Equipment list/details | [~] | Equipment profile aggregates shifts, actual expenses, stock issues, drivers, and results; no duplicate standalone operation screen |
| Driver account | [~] | Aggregate shift wages, advances, and payments into a statement |
| Advances | [~] | Separate driver receivable/advance workflow, never an operating expense by default |
| Expenses | [~] | One transaction can be linked to equipment and shift simultaneously; avoid duplicate posting |
| Custom categories | [~] | Tenant-specific labels/categories are configurable; accounting transaction types remain protected |
| Inventory | [~] | Cover stock movement and direct issue without a store where supported |
| Treasury and banks | [ ] | Separate account balances and movements |
| Reports | [ ] | Confirm report definitions against accounting backend |
| Platform Owner Web | [ ] | Separate responsive web design scope |

## Decisions recorded on 2026-11-03

- Driver wage is variable per shift. A suggested/default value may be offered, but the final wage is snapshotted on the shift and does not change when the driver's profile defaults change.
- A driver's account statement separates earned shift wages, advances, and actual payments. Partial payments are supported conceptually; the remaining balance stays outstanding.
- An advance is a separate movement against the driver's balance, not an operating expense by default.
- Users may add, rename, reorder, and deactivate tenant-specific categories such as fuel, maintenance, washing, and advance reasons.
- Core accounting transaction types (for example expense, advance, payment, income, and stock movement) are protected. Users cannot convert one transaction type into another by renaming a category.
- Used categories are deactivated rather than deleted; historical transactions retain their original meaning and audit trail.
- An expense may be linked to equipment, a shift, both, or neither (company-wide). Linking the same expense to both equipment and shift creates one financial transaction, not two.
- Equipment profile is an aggregation/read model for equipment-related activity. The shift remains the operational record; do not create a duplicate standalone operation entry solely to repeat shift data.
- Stock purchase and stock consumption are distinct. Purchase adds inventory (or creates a supplier payable); issuing/consuming stock records usage according to the accounting policy. Direct purchase-and-use without a store should be supported where the workflow allows it.
- Estimated equipment costs must not be posted as actual expenses or counted again when actual expenses already exist.

## Product and accounting guardrails

- A completed shift with a zero wage/fee must follow the backend's explicit business rule; the UI must not silently hide or invent a journal entry.
- Editing a completed shift's operational or financial values must use the approved correction flow when required, including reason/audit/version/idempotency behavior supported by the backend.
- Driver advances are not automatically operating expenses.
- Estimated equipment cost must not be posted as an actual expense.
- If actual equipment expenses are already recorded, do not count an estimate again.
- A cash balance is not the same thing as revenue or profit.
- Do not display prototype sample values as real customer data.
- Financial records are displayed in multiple screens through references/aggregations; they must never be duplicated in the ledger because they appear in more than one place.
- Historical posted transactions must not be silently rewritten by changing a category label or default setting.

## Approval and implementation workflow

1. Build/revise the interactive prototype for one screen or flow.
2. Review it with the product owner.
3. Record explicit approval here and in that screen's specification.
4. Confirm the relevant API/backend behavior and accounting posting rules.
5. Implement in Flutter in a separate, reviewed code change.
6. Test on the target Android device and update status only after verification.

## Change log

- 2026-11-03 — Initial design tracking created; ERP-inspired mobile direction selected; Design System v1.0 established.
- 2026-11-03 — Added driver account, configurable categories, expense linking, equipment profile, and inventory workflow decisions. These are design specifications, not application implementation.
