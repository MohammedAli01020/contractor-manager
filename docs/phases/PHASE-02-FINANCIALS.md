# Phase 02 — Financial foundation and monthly close

**Roadmap:** [PRODUCT_ROADMAP.md](../PRODUCT_ROADMAP.md#phase-2--financial-foundation-driver-ledger-equipment-expenses-and-monthly-closing)  
**Status:** In progress — accounting core, default chart and initial operational journal links implemented and CI-verified; driver-ledger completion, cash/bank workflows, inventory and monthly close remain in scope; the initial tenant-scoped trial-balance report is implemented and CI-verified.

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

## Trial balance report\n- [x] Tenant-scoped read-only report with opening balance, period debit/credit movement, closing balance and totals.\n- [x] Inclusive accounting-date boundaries, zero-balance accounts, historical inactive accounts and tenant isolation.\n- [x] Read permissions for admin/operator/viewer; no write endpoint.\n- [ ] Add account-level statement drill-down and reconciliation workflow.\n\n## Monthly close
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

## Implementation plan and current evidence
- Design baseline: [FINANCIAL-FOUNDATION-DESIGN.md](FINANCIAL-FOUNDATION-DESIGN.md).
- Baseline inspected: tenant-scoped entities, `BaseTenantEntity`, `Money`, existing shift snapshots, user identity and Flyway migrations through V16.
- Initial accounting-core code is present on `feature/financial-foundation`: additive migration V17 creates tenant-scoped chart of accounts, journal entries and journal lines; Java models/repositories and `JournalPostingService` validate balanced lines, account/driver/equipment tenant ownership, idempotency keys and exact reversal lines. Initial validation tests were added.
- **Verification evidence (2026-10-31):** GitHub Actions push run [41227488393](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41227488393) and PR run [41227514282](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41227514282) succeeded on code commit `a331a6a61c56f753cfd7e1cf770ab21ba12fd5fa`. Surefire artifact contained 29 XML reports: 168 tests, 0 failures, 0 errors and 0 skipped. This includes 8 default-chart unit tests, 27 shift integration tests, 10 expense integration tests, 8 journal-concurrency integration tests and 1 V24 existing-data migration test; CI execution guards passed.
- Trial balance report is implemented on `feature/trial-balance-report`: `GET /api/v1/accounting/reports/trial-balance?from=YYYY-MM-DD&to=YYYY-MM-DD`. It aggregates posted journal rows in MySQL, scopes by authenticated tenant, includes all chart accounts, normalizes opening/closing debit-credit balances, and reports whether closing totals balance. CI verification passed on 2026-10-31 (push run [41229634484](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41229634484), PR run [41229638627](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41229638627)); 7 MySQL integration tests passed.\n- Tenant default chart initialization is wired into both tenant-creation paths and the shift-wage fallback path. The baseline includes cash/bank, receivables, fixed assets, recoverable worker advances, inventory, supplier/driver/owner payables, capital, shift/other revenue, driver wages and operating/general expenses. Stable system-account codes are centralized and validated (tenant, active state, system-managed flag, account type and cash/bank classification) before shift wages, driver advances/payments and expense/payments are posted. Implementation is on `feature/accounting-default-chart-completion` and CI-verified on `a331a6a61c56f753cfd7e1cf770ab21ba12fd5fa`. PR #18 is Draft and stacked on PR #17; neither PR is merged.

## Dependencies and next action
Depends on stable Phase 0/1 behavior and a verified schema baseline. Balance sign convention is defined in the design as positive = amount payable to driver; payment/advance/deduction reduce the payable. Recognition date is separate from payment date. Monthly close/reopen is controlled and audited. Before schema changes, inspect the current code, migrations and tests, then implement the smallest reviewable accounting-core slice.
