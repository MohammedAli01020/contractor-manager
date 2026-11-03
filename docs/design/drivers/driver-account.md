# Driver Account — Design Specification

**Status:** Draft / product direction reviewed  
**Platform:** Flutter mobile app  
**Design baseline:** [Design System v1.0](../design-system/v1.0.md)

## 1. Purpose

Give the contractor a reliable statement for each driver, combining earned shift wages, advances, and actual payments while keeping each movement type distinct.

## 2. Driver wage on a shift

- Driver wage is variable per shift; it is not assumed to be a fixed immutable driver property.
- The UI may suggest a default wage from a driver profile or another configured rule when available.
- The user can enter or override the wage for the current shift according to permissions.
- The final agreed wage is snapshotted on the shift. Later changes to a driver's suggested/default wage must not rewrite historical shifts.
- Zero-value wage behavior follows the backend's explicit business rule; the UI must not invent a payable or journal entry.

## 3. Account summary

Show separate totals for:
- Earned wages from completed/eligible shifts.
- Advances issued to the driver.
- Payments made to the driver.
- Remaining net payable/receivable balance, with clear sign and wording.

The balance must be derived from posted source movements, not independently editable as a number.

## 4. Statement movements

Each row should identify:
- Date and reference.
- Movement type (shift wage, advance, payment, adjustment where supported).
- Description and related shift/equipment when relevant.
- Debit/credit direction or an unambiguous plus/minus convention.
- Amount and posting/status information.
- Link to the source record.

## 5. Advances and payments

- An advance is a separate movement against the driver's account; it is not an operating expense by default.
- A payment settles an existing payable or driver balance; it must not create a second wage expense.
- Support full and partial payments where backend workflows permit.
- After a partial payment, the unpaid balance remains outstanding.
- Record the source cash/bank account for each actual payment or advance disbursement.
- Do not treat an unpaid wage as a cash outflow.

## 6. Permissions and corrections

- Creating advances, recording payments, and correcting posted movements should have explicit permissions.
- Posted financial movements must not be silently edited or deleted.
- Corrections should use the backend's supported reversal/adjustment and audit workflow.

## 7. Acceptance checklist

- [ ] Wage is entered/confirmed per shift.
- [ ] Historical wage snapshots do not change with profile defaults.
- [ ] Advances remain separate from operating expenses.
- [ ] Payments reduce the outstanding balance without duplicating wage expense.
- [ ] Partial payments leave the correct remaining balance.
- [ ] Statement rows link to source records.
- [ ] Loading, empty, offline, and error states are specified.
- [ ] RTL and small-screen behavior reviewed on Android.
- [ ] Product owner explicitly approves the screen.
