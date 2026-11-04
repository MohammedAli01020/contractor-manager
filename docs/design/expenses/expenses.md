# Expenses and Configurable Categories — Design Specification

**Status:** Draft / product direction reviewed  
**Platform:** Flutter mobile app  
**Design baseline:** [Design System v1.0](../design-system/v1.0.md)

## 1. Purpose

Make daily expense capture quick on mobile while preserving accounting meaning, traceability, and the ability to analyze costs by company, equipment, and shift.

## 2. Expense capture fields

The proposed flow includes:
- Expense category.
- Amount and currency.
- Transaction date.
- Description/notes and optional attachment where supported.
- Association scope:
  - Equipment only.
  - Shift only.
  - Equipment and shift together.
  - Company-wide / no equipment or shift.
- Payment status/source:
  - Paid from cash.
  - Paid from bank.
  - Unpaid/on account, where supplier payable workflow is supported.

The form should reveal equipment/shift selectors only when relevant. Do not force users to choose an equipment or shift for a general company expense.

## 3. Single-transaction rule

One expense can be linked to both an equipment and a shift. Both screens may display the same source expense, but the system must create one financial transaction and one corresponding posting flow only. Reports must aggregate by the transaction identity, not by the number of screens that reference it.

## 4. User-configurable categories

Each tenant/company may manage its own categories, for example:
- Fuel.
- Maintenance.
- Oils and lubricants.
- Equipment washing.
- Transport.
- Other company-defined categories.

Users may add categories, rename them, reorder them, and deactivate them, subject to permissions.

### Protected accounting semantics

Categories are labels/classifications, not transaction types. Users must not be able to convert an advance into an expense, a payment into income, or a stock movement into an expense merely by changing a category name.

Core transaction types and posting behavior remain controlled by the application/accounting configuration. Any permitted account mapping changes require elevated permissions, validation, and an auditable workflow.

### Historical integrity

- Do not hard-delete a category referenced by posted transactions.
- Deactivate it for future use instead.
- Renaming a category must not silently alter the accounting nature or rewrite historical postings.
- Preserve the original transaction and audit history.

## 5. Inventory boundary

- Buying an item for stock is a purchase/inventory receipt and may create a supplier payable or cash/bank movement.
- Consuming/issuing the item is a separate stock movement; its cost is recognized according to the accounting policy.
- Do not record the same item as a full purchase expense and then as a second full expense on issue.
- Support a direct purchase-and-use path without a store when the business workflow and accounting rules allow it.

## 6. Estimates versus actual expenses

Estimated equipment cost is not a posted actual expense. If actual expenses are recorded, do not add an estimate on top of them as though it were another expense. The UI must label estimates clearly and keep them out of actual posted totals.

## 7. Acceptance checklist

- [ ] Expense can be company-wide, shift-linked, equipment-linked, or linked to both.
- [ ] One source expense is counted once across screens and reports.
- [ ] Paid and unpaid states are distinct.
- [ ] Cash and bank sources remain distinguishable.
- [ ] Tenant-specific categories can be managed with permissions.
- [ ] Core transaction types cannot be changed by renaming categories.
- [ ] Referenced categories are deactivated rather than deleted.
- [ ] Stock purchase and consumption remain distinct.
- [ ] Estimates are not treated as actual posted expenses.
- [ ] RTL, offline, validation, and error states are specified.
- [ ] Product owner explicitly approves the screen.
