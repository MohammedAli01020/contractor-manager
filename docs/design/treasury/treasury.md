# Treasury and Bank Accounts — Design Specification

**Status:** Draft / product direction under review  
**Platform:** Flutter mobile app  
**Design baseline:** [Design System v1.0](../design-system/v1.0.md)

## 1. Purpose

Give the contractor a trustworthy view of available money and a traceable record of every receipt, payment, and transfer, while keeping cash and each bank account distinct.

## 2. Account model

The screen should list separate financial accounts, for example:
- Main cashbox.
- Site cashbox, where the business needs more than one physical cash location.
- One or more bank accounts.

Each account has its own balance, currency, active status, and movement statement. A combined cash-and-bank total may be shown as an additional figure, but must never replace or obscure the individual balances.

Creating or deactivating an account requires suitable permissions. An account with posted movements must not be hard-deleted.

## 3. Main screen

Proposed information:
- Separate balances for every cashbox and bank account.
- Optional combined liquid-funds total, clearly labelled.
- Recent receipts, payments, and transfers.
- Date/account filters.
- Quick actions: receive, pay, transfer, and open statement.
- Clear loading, empty, offline, and error states.

The balance displayed must come from posted account movements, not a separately editable balance field.

## 4. Receipt and payment workflows

### Receipt
Capture amount, date, source/description, destination account, and reference/attachment where supported. When the receipt settles an existing customer receivable or another source transaction, link it to that source instead of creating a second revenue event.

### Payment
Capture amount, date, paying account, recipient, purpose, and source transaction where applicable. Examples include paying a driver, supplier, or operating expense.

- Paying a driver reduces the outstanding driver payable; it does not create a second wage expense.
- Paying an already-recorded expense settles the payable; it must not duplicate the expense.
- A payment made immediately with an expense can be represented in one coherent workflow, with one expense and its corresponding cash/bank movement.
- An unpaid expense must not reduce cash or bank balance until payment occurs.

## 5. Transfers between accounts

A transfer from cashbox A to bank account B is an internal movement:
- Decrease account A.
- Increase account B.
- Record one transfer identity with linked source and destination legs.
- Do not classify the transfer as revenue or expense.
- Prevent selecting the same account as both source and destination.
- Require an auditable correction/reversal workflow for posted transfers; do not silently edit historical balances.

## 6. Opening balances and reconciliation

- Initial account balances must be entered through a controlled opening-balance workflow with an effective date, source/reference, and appropriate accounting counterpart.
- Never allow users to directly overwrite a live account balance.
- Provide statement views with opening balance, receipts, payments, transfers, and closing balance for a selected period.
- Bank reconciliation and statement matching may be a later phase; do not imply reconciliation is complete unless the workflow exists.

## 7. Permissions and audit

Use separate permissions for viewing balances, receiving money, making payments, transferring funds, managing accounts, and correcting posted movements. Record actor, timestamp, source account, destination account, amount, and reason/reference as applicable.

## 8. Accounting and UI guardrails

- Cash and bank balances are asset balances, not revenue or profit.
- Revenue/expense recognition is separate from cash collection/payment timing.
- A driver wage payable is not a cash outflow until payment is recorded.
- A driver advance is a separate driver-account movement and cash/bank disbursement; it is not automatically an operating expense.
- Transfers between company-owned accounts do not change profit.
- The same source expense, wage, receivable, or payable must not be counted again when it is paid.
- Posted movements must be corrected through supported reversal/adjustment flows, preserving the audit trail.

## 9. Acceptance checklist

- [ ] Multiple cash and bank accounts can be represented independently.
- [ ] Each account has its own statement and derived balance.
- [ ] Optional combined liquid-funds total is clearly distinguished from individual balances.
- [ ] Receipts and payments link to source transactions where applicable.
- [ ] Paying a driver settles the payable without duplicating wage expense.
- [ ] Paying an expense settles the payable without duplicating expense.
- [ ] Unpaid expenses do not reduce cash/bank balances.
- [ ] Transfers create paired account movements and no revenue/expense.
- [ ] Opening balances use a controlled, auditable workflow.
- [ ] Posted movements cannot be silently edited or deleted.
- [ ] Permissions, offline behavior, and error states are specified.
- [ ] RTL and small-screen behavior reviewed on Android.
- [ ] Product owner explicitly approves the screen.
