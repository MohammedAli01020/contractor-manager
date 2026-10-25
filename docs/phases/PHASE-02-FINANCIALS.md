# Phase 02 — Financial foundation and monthly close

**Roadmap:** [PRODUCT_ROADMAP.md](../PRODUCT_ROADMAP.md#phase-2--financial-foundation-driver-ledger-equipment-expenses-and-monthly-closing)  
**Status:** Not started.

## Goal
Build auditable financial ledgers and monthly closing without double-counting shift wages, expenses, payments, or inventory consumption.

## Agreed financial rules
- Equipment operating result = completed shift values due to the equipment before driver wages/expenses, minus driver wages attributable to those shifts, minus recognized equipment expenses.
- Deduct each shift's driver wage from equipment operating result exactly once. Record the wage as a driver payable; do not subtract it a second time from the driver's balance.
- Keep equipment operating result, cash movement, unpaid liabilities, and owner/partner settlement amounts distinct. An owner's amount due is determined by the actual contract/settlement rule, not automatically equated to operating result.
- Support company-owned and third-party/partner-owned equipment.
- Expenses can be linked directly to equipment or to a specific shift. Payment status is separate from expense recognition period; paying an older expense must not create a new expense in the payment period.
- Preserve current shift snapshots and existing fields/API values such as `defaultShiftPrice`, `operatingCostPerShift`, and `equipmentOperatingCost`. Keep estimated operating cost separate from actual expenses to prevent double deductions.
- Posted financial movements are not silently deleted/edited. Corrections use audited reversal/correction transactions.

## Driver ledger scope
- [ ] Opening balance with clear sign convention.
- [ ] Completed-shift wages.
- [ ] Advances, approved deductions, bonuses/allowances, actual payments.
- [ ] Adjustments, settlements, reversals and correction reason/audit metadata.
- [ ] Driver statement shows opening balance, movements, totals and closing balance.
- [ ] Closing balance carries forward as next period's opening balance without duplicating original transactions.

## Equipment expenses and ownership
- [ ] Configurable expense categories (initial examples: diesel/fuel, oils/grease, maintenance, spare parts, custom categories).
- [ ] Expense entry can reference equipment or a specific shift; avoid recording the same expense twice.
- [ ] Unpaid, partially paid and fully paid states are represented independently from recognition period.
- [ ] Historical transaction descriptions remain stable when a category is renamed/deactivated; a category referenced by transactions cannot be deleted.
- [ ] Ownership type and partner/owner details are represented when relevant.
- [ ] Owner/partner settlement is separately calculated, approved and audited.

## Monthly close
- [ ] Reconciliation preview and explicit exception list before closing.
- [ ] Closing period, approver, approval timestamp, balances and relevant totals are snapshotted immutably.
- [ ] Prevent closing while unapproved exceptions remain.
- [ ] Carry balances forward without re-posting original movements.
- [ ] Reopening requires permission, reason, audit history, and controlled correction behavior.
- [ ] Close/post/payment operations are idempotent and safe to retry after weak network or timeouts.

## Security and acceptance criteria
- [ ] Tenant isolation and backend-enforced role permissions for all financial operations.
- [ ] Audit actor, timestamp, reference, status and reason are captured for applicable movements.
- [ ] Automated tests cover positive/negative balances, double posting, duplicate retries, payment-vs-expense timing, corrections, carry-forward and owner settlement.
- [ ] Example cases reconcile across driver ledger, equipment result, owner/partner amounts due, unpaid liabilities and monthly closing.
- [ ] No double deduction and no duplicated transaction when a request is retried.

## Dependencies and next action
Depends on stable Phase 0/1 behavior and explicit agreement on balance sign conventions, recognition-period rules, and monthly close/reopen policy. Before implementation, inspect current code/schema/migrations and propose the entity model, API, migrations, and tests for approval.
