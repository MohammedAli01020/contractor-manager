# Phase 02 — Financial foundation and monthly close

**Roadmap:** [PRODUCT_ROADMAP.md](../PRODUCT_ROADMAP.md#phase-2--financial-foundation-driver-ledger-equipment-expenses-and-monthly-closing)  
**Status:** In progress — accounting core and tenant-scoped API code exist on feature branches; Flutter read-only accounting UI exists and Flutter CI passed twice on the recorded UI commit. Full accounting acceptance, backend/MySQL integration, and real-device/API verification are still pending.

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

## Implementation plan and current evidence
- Design baseline: [FINANCIAL-FOUNDATION-DESIGN.md](FINANCIAL-FOUNDATION-DESIGN.md).
- Baseline inspected: tenant-scoped entities, `BaseTenantEntity`, `Money`, existing shift snapshots, user identity and Flyway migrations through V16.
- Initial accounting-core code is present on `feature/financial-foundation`: additive migration V17 creates tenant-scoped chart of accounts, journal entries and journal lines; Java models/repositories and `JournalPostingService` validate balanced lines, account/driver/equipment tenant ownership, idempotency keys and exact reversal lines. Initial validation tests were added.
- **Accounting API:** proposed in [PR #14](https://github.com/MohammedAli01020/contractor-manager/pull/14), based on the accounting API foundation. It exposes tenant-scoped account and journal list/detail reads plus ADMIN-only journal posting. Keep the PR open as a draft until review and verification are complete.
- **Flutter accounting UI:** proposed in [PR #15](https://github.com/MohammedAli01020/contractor-manager/pull/15), based on `feature/accounting-mobile-foundation`. It adds Arabic screens for chart of accounts, journal list and journal details, plus retry/error handling and a Flutter CI workflow. On the recorded UI commit, Flutter 3.3.3 `flutter analyze --no-fatal-infos` and `flutter test` each passed in two workflow runs. This is CI evidence, not confirmation of a real-device run or a live backend connection.
- **Still to verify:** run/confirm backend accounting tests and Flyway V17 against MySQL; exercise tenant boundaries and ADMIN-only posting; run the app on a real Android phone over Wireless ADB with a reachable backend; verify account and journal responses end-to-end. Do not mark the whole phase tested until these checks and the phase reconciliation scenarios pass.
- **Manual journal-entry UI (implemented; CI pending):** the Flutter accounting screen now exposes a create action only when the authenticated user's role is `ADMIN`. The form loads active tenant accounts, captures accounting date and description, supports adding/removing lines (minimum two), validates each line and balances amounts using integer cents, creates a stable posting key for retries within the form session, and posts `sourceType=MANUAL` to the existing API. The journal list refreshes only after a successful post. Backend validation and ADMIN authorization remain authoritative. Real-device and live-API posting verification are still required.
- **Default chart initialization for existing tenants:** implemented on the accounting API branch as an ADMIN-only endpoint that initializes missing baseline accounts for the authenticated tenant, preserves existing/custom accounts and returns the refreshed chart. Flutter exposes the action when the account list is empty. Backend CI passed for the endpoint commit; repeat-request behavior, role enforcement and tenant isolation still need end-to-end verification.
- After verification, proceed to the ADMIN manual journal-entry form before connecting completed shifts, driver payables and expenses.

## Dependencies and next action
Depends on stable Phase 0/1 behavior and a verified schema baseline. Balance sign convention is defined in the design as positive = amount payable to driver; payment/advance/deduction reduce the payable. Recognition date is separate from payment date. Monthly close/reopen is controlled and audited. Before schema changes, inspect the current code, migrations and tests, then implement the smallest reviewable accounting-core slice.
