# Equipment Profile — Design Specification

**Status:** Draft / product direction reviewed  
**Platform:** Flutter mobile app  
**Design baseline:** [Design System v1.0](../design-system/v1.0.md)

## 1. Purpose

Provide one equipment profile that helps the contractor understand the equipment's identity, availability, work history, actual costs, and financial contribution without entering the same operation twice.

## 2. Core product decision

The shift is the operational record. Do not create a separate standalone "equipment operation" entry that duplicates shift information. The equipment profile aggregates and links to existing shifts, expenses, inventory movements, and related records.

## 3. Proposed profile structure

1. **Identity and status**
   - Equipment name/code/type.
   - Ownership or rental context where supported.
   - Active/inactive and operational availability status.
   - Key identifying details; avoid overloading the first screen.
2. **Activity summary**
   - Shift count for the selected period.
   - Completed and in-progress shifts, clearly separated.
   - Revenue only where supported by actual shift/customer data.
3. **Financial summary**
   - Actual equipment-linked expenses.
   - Stock consumption cost when available from inventory/accounting.
   - Revenue and net operating result only when definitions are verified.
   - Estimated costs must be visibly estimates and excluded from posted actual expenses.
4. **Related records**
   - Shifts involving the equipment.
   - Expenses linked to the equipment.
   - Stock issues/consumption linked to the equipment.
   - Drivers who used it, with date/shift context.
5. **Reports**
   - Period filters.
   - Breakdown by expense category.
   - Drill-through to the source transaction.

## 4. Expense and shift linking

An expense may be associated with:
- Equipment only (for example periodic maintenance).
- Shift only (for example a site-specific expense not assigned to equipment).
- Both equipment and shift (for example fuel used during a specific shift).
- Neither, as a company-wide expense.

When an expense is linked to both an equipment and a shift, it remains one financial transaction. The equipment profile and shift details display references to the same expense; they must not create two ledger postings.

## 5. Inventory behavior

- Stock purchase and stock consumption are distinct events.
- Issuing a spare part to equipment records a stock movement and its usage context.
- If a stock issue is linked to a shift, the same movement may be visible from both the equipment and shift screens.
- Direct purchase-and-use without a store should be supported as a separate workflow when permitted by accounting policy.
- Do not count a purchase as an operating expense and then count the same item's issue as a second expense.

## 6. UI states

Specify loading, empty, offline, permission-denied, and error states. A lack of linked transactions must not be presented as zero profitability unless the data period and accounting coverage are known.

## 7. Acceptance checklist

- [ ] Equipment identity and status are clear.
- [ ] Shift history links to the original shift records.
- [ ] Expense history links to the original expense records.
- [ ] A single expense linked to both equipment and shift is counted once.
- [ ] Stock purchase is not confused with stock consumption.
- [ ] Estimates are not mixed with posted actual expenses.
- [ ] Financial totals match backend/accounting definitions.
- [ ] RTL and small-screen behavior reviewed on Android.
- [ ] Product owner explicitly approves the screen.
