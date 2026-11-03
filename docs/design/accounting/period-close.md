# Accounting Period Close — Monthly and Annual

Status: **Draft for product-owner review**  
Scope: product behavior, accounting controls, and mobile workflow. This document is a design specification; it does not assert that these controls are implemented.

## 1. Purpose

Period close gives the contractor a repeatable way to review operational and financial activity, identify missing or inconsistent records, and protect reported results after approval.

The close process must preserve the accounting source of truth. It must not create duplicate expenses, wages, receipts, payments, stock movements, or journal entries merely because a period is being reviewed or closed.

## 2. Period model and states

Each tenant has its own accounting periods and close status. The initial user experience is monthly, with annual close built on the same controls.

Proposed states:

1. **Open** — normal permitted posting and correction workflows.
2. **Under review** — a reviewer is checking the period. New activity may still be recorded only if the configured cutoff policy permits it; the UI must clearly show that the review is no longer clean if relevant data changes.
3. **Closed** — the period is approved and protected from ordinary backdated posting, editing, and deletion.
4. **Reopened** — an authorized user has explicitly reopened the period with a reason; all actions remain auditable.

A period has a start date, end date, tenant-local timezone/cutoff, status, and close metadata. Date boundaries must be unambiguous and consistent across API, database, and reports.

The system must distinguish:
- **Effective/source date** — when the underlying business event occurred.
- **Posting date** — the accounting period in which its financial effect is recognized.

A late document or correction must not silently rewrite a closed period. Its treatment follows the correction policy below.

## 3. Roles and approvals

- A user may prepare/review a period only if granted the relevant permission.
- Closing and reopening are privileged actions, separate from ordinary transaction entry.
- The person closing a period and the timestamp must be recorded.
- Where the contractor has multiple authorized users, support separation between preparer and approver as a configurable control. A small contractor may use a single authorized owner, but the audit trail remains mandatory.
- Reopening requires an explicit reason and records who authorized it, when, and which period was affected.

Permission names and role mapping must be aligned with the existing authorization model before implementation.

## 4. Monthly close workflow

### Step A — Start review

The user selects a month and sees:
- period boundaries and timezone;
- current status;
- last activity affecting the period;
- counts of records awaiting review or completion;
- unresolved close exceptions;
- previous close/reopen history.

Starting review does not itself post accounting entries or change balances.

### Step B — Operational completeness

Review the source records that can create financial effects:

- Shifts: completed, cancelled, zero-wage/zero-fee, and corrected shifts follow explicit business rules.
- Drivers: earned wages, advances, payments, and remaining balances reconcile to driver statements.
- Equipment: linked shifts, actual expenses, and stock issues are traceable without duplicating a source transaction.
- Expenses: each expense has a valid date, amount, classification, and payment status; a single expense linked to both equipment and shift remains one transaction.
- Inventory: receipts, issues, transfers, returns, and stocktake adjustments are recorded with their source and audit trail.
- Treasury and banks: receipts, payments, and internal transfers are recorded against the correct accounts.
- Customers and suppliers: source obligations and settlements are reviewed where those workflows exist.

The checklist must show **Not applicable** separately from **Not reviewed**. An empty list is not automatically evidence that the user reviewed that area.

### Step C — Financial reconciliation

At minimum, provide or validate:

- Trial Balance for the period/end date;
- General Ledger and journal-entry drill-down;
- Profit and Loss for the period;
- Balance Sheet as of period end;
- Cash/bank account statements and derived balances;
- Driver balances and wage/advance/payment movements;
- Customer receivables and supplier/payable balances, when enabled;
- Inventory quantities and valuation only when the valuation policy and cost data are complete;
- relevant audit log and correction history.

Every total must drill down to source records and journal lines. One source transaction referenced from multiple screens must not be counted more than once.

### Step D — Exceptions

The system must prevent closing while any blocking exception remains, including:

- unbalanced journal entries;
- missing or invalid required account mappings;
- duplicate posting for the same source event;
- unresolved posting or source-link integrity failures;
- a material accounting inconsistency detected by reconciliation;
- unauthorized or invalid backdated activity;
- incomplete required close checklist items.

Warnings that do not invalidate the accounting result may be acknowledged with a reason and remain visible in the close report. The distinction between blocking errors and acknowledged warnings must be explicit; users must not be able to bypass a blocking accounting-integrity failure.

### Step E — Approve and close

Before confirmation, show a review summary:
- period and cutoff;
- Trial Balance debit/credit totals and difference;
- unresolved blocking errors and acknowledged warnings;
- key report totals;
- number of source transactions and journal entries;
- preparer/reviewer identity where applicable.

On successful close, persist an immutable close event/snapshot sufficient to reproduce what was reviewed, including the report parameters and relevant source/version identifiers. Do not rely only on a mutable status flag.

## 5. Behavior after close

For a closed period:

- Ordinary edits and deletion of posted financial records are prohibited.
- New backdated transactions affecting the closed period are blocked by default.
- Posted journal entries are not edited in place or silently deleted.
- The application explains why an action is blocked and offers the permitted correction path.
- Historical reports remain reproducible and identify any later-period correction that relates to the closed period.

### Correction policy

Default policy: record the correction in the **first open period**, preserving both the original effective date and the actual posting date, with a reason and reference to the original transaction.

An authorized user may instead reopen a period when a genuine restatement is required. Reopening must:
1. require permission and a non-empty reason;
2. record actor, timestamp, period, and reason;
3. invalidate or supersede the previous close snapshot;
4. mark dependent reports as needing review;
5. require the period to pass the close checks again;
6. preserve the complete history of prior close and reopen events.

The product must not silently choose between a current-period correction and a restatement. The chosen treatment must be visible and auditable.

## 6. Daily cash review (optional)

A daily cash/bank reconciliation may be offered independently of monthly close. It is a control/checklist, not a second source of truth and not an automatic journal-posting process.

- Compare recorded receipts/payments with the actual cash count or bank statement.
- Record counted/statement balance, system-derived balance, difference, explanation, reviewer, and timestamp.
- A difference is corrected through an explicit auditable transaction; never overwrite the derived account balance.
- Daily review does not close the accounting month.

## 7. Annual close

Annual close reuses the monthly controls and adds:

- confirm all twelve monthly periods are closed or explicitly accounted for;
- review annual Trial Balance, General Ledger, Profit and Loss, and Balance Sheet;
- reconcile cash/banks, driver balances, receivables/payables, and inventory;
- review fixed-asset/depreciation and tax workflows only if those modules are implemented and configured;
- calculate and post year-end profit/loss closing and retained-earnings/equity transfer according to the approved chart-of-accounts and accounting policy;
- ensure the year-end closing journal is balanced, uniquely posted, and linked to the year-end close;
- preserve prior-year reports and opening balances for the next fiscal year.

Year-end closing must not duplicate or erase the underlying revenue/expense history. The exact retained-earnings accounts and treatment must be confirmed against the default chart of accounts and local accounting requirements before implementation.

## 8. Data integrity and technical requirements

- Tenant isolation applies to periods, close snapshots, exceptions, approvals, and audit events.
- Close/reopen operations must be transactional and safe under concurrent requests and multiple application instances.
- Use idempotent command handling so retries cannot create duplicate close events or year-end journals.
- Enforce period and posting constraints in the backend/database; Flutter validation alone is insufficient.
- A concurrent transaction must not slip into a period between final validation and close. Use a documented locking/serialization strategy appropriate to the database and posting workflow.
- Every financial source event has at most one effective posting for its posting identity; uniqueness conflicts must resolve by verifying/retrieving the original result rather than creating a second journal.
- Close snapshots and audit events are append-only from the application perspective.
- Reports must use consistent period boundaries and must not mix effective-date and posting-date semantics without labeling them.

## 9. Mobile experience

The contractor's primary workflow is mobile-first:

- Show a clear month selector and status at the top.
- Present a short readiness summary before detailed sections.
- Group checklist items by Shifts, Drivers, Equipment, Expenses, Inventory, Treasury, Customers/Suppliers, and Accounting.
- Each exception opens the source record or a focused explanation/action.
- Use clear labels for blocking errors, warnings, and not-applicable sections.
- Require a deliberate confirmation step for close and reopen; never make these actions a swipe-only or accidental tap.
- Keep the close report readable on a phone and allow drill-down to the underlying records.

Platform Owner Web is a separate product surface; tenant close permissions remain tenant-scoped.

## 10. Acceptance criteria

- A period cannot be closed with an unbalanced journal or a blocking integrity exception.
- Closing does not create duplicate operational or financial transactions.
- A closed period cannot be silently edited, deleted, or backdated into through ordinary workflows.
- A permitted correction preserves effective date, posting date, reason, actor, and source linkage.
- Reopen and re-close history remains inspectable.
- Reports for a closed period are reproducible and reconcile to the journal/source records.
- Duplicate or concurrent close requests do not create duplicate close events or year-end entries.
- Tenant A cannot view or alter Tenant B's period, close snapshot, or exceptions.
- Annual close carries forward balances correctly without erasing prior-year detail.
- Flutter and backend enforce the same rules, with backend/database enforcement authoritative.

## 11. Decisions still requiring explicit product/accounting confirmation

These are deliberately not hard-coded as final policy in this draft:

1. Whether all tenants use calendar months/years initially or can configure a fiscal year start.
2. Whether the single-owner workflow may both prepare and approve, with an explicit audit marker.
3. The materiality threshold, if any, for warnings that may be acknowledged.
4. Which correction cases require a restatement/reopen rather than first-open-period posting.
5. Exact year-end closing accounts and local tax/accounting treatment.
6. Whether daily cash review is enabled by default or optional per tenant.

These decisions must be confirmed with the accounting policy and existing backend behavior before implementation.
