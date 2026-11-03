# Design Progress — Contractor Manager

Last updated: 2026-11-03  
Current design baseline: [Design System v1.0](design-system/v1.0.md)

## Status legend

- [ ] Not started
- [~] Draft / in review
- [x] Approved
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

## Screens

| Screen / flow | Status | Next action |
|---|---|---|
| Dashboard | [~] | Refine and approve layout, metrics, and quick actions |
| Main navigation / More | [~] | Validate destination grouping and reachability |
| Shifts list | [ ] | Define filters, statuses, and row/card layout |
| Create shift | [ ] | Align form with actual backend validation and shift rules |
| Shift details | [ ] | Show operational, driver, equipment, and financial information |
| Completed shift correction | [ ] | Reflect correction workflow and audit requirements; ordinary edit remains distinct |
| Equipment list/details | [ ] | Include equipment-linked expense history |
| Drivers/workers | [ ] | Define profiles and balances |
| Advances | [ ] | Keep advances distinct from operating expenses |
| Expenses | [ ] | Separate equipment-linked and general expenses |
| Inventory | [ ] | Cover stock movement and direct issue without a store where supported |
| Treasury and banks | [ ] | Separate account balances and movements |
| Reports | [ ] | Confirm report definitions against accounting backend |
| Platform Owner Web | [ ] | Separate responsive web design scope |

## Product and accounting guardrails

- A completed shift with a zero wage/fee must follow the backend's explicit business rule; the UI must not silently hide or invent a journal entry.
- Editing a completed shift's operational or financial values must use the approved correction flow when required, including reason/audit/version/idempotency behavior supported by the backend.
- An advance is not automatically an expense.
- Estimated equipment cost must not be posted as an actual expense.
- If actual equipment expenses are already recorded, do not count an estimate again.
- A cash balance is not the same thing as revenue or profit.
- Do not display prototype sample values as real customer data.

## Approval workflow

1. Build/revise the interactive prototype for one screen or flow.
2. Review it with the product owner.
3. Record explicit approval here and in that screen's specification.
4. Confirm the relevant API/backend behavior.
5. Implement in Flutter in a separate, reviewed code change.
6. Test on the target Android device and update status only after verification.

## Change log

- 2026-11-03 — Initial design tracking created; ERP-inspired mobile direction selected; Design System v1.0 established.
