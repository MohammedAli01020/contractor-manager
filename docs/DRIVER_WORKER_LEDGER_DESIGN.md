# Driver and Worker Ledger – Design Notes

Status: incremental implementation on `feature/driver-worker-ledger`; initial ledger/advance tests and the first posted-shift API guard tests passed. MySQL database-guard migration and direct-SQL integration tests are now added; CI verification is pending.
Base: `feature/equipment-expenses-accounting`.
This document does not change `main` and does not merge any pull request.

## Existing behavior verified

- `ShiftService.finish()` currently changes a shift from `IN_PROGRESS` to `COMPLETED`, stamps `finishedAt`, and records an audit event. It does not create an accounting journal or worker-ledger transaction.
- A shift stores a snapshot `driverWage` when the shift starts. Later changes to the driver's rate do not reprice that shift.
- `ShiftService.update()` currently permits updates to completed shifts, including driver, shift type, and wage. This must be handled before a completed shift can create an immutable payable.
- A completed shift can be cancelled and reinstated; reinstating a previously completed shift restores it as completed.
- Chart of accounts already contains 2100 (driver payables) and 5000 (driver wage expense).
- Journal lines support `driver_id`, allowing the general ledger to retain the driver dimension.

## Proposed first release

- Shift-based wage calculation only, using the persisted `Shift.driverWage` snapshot. Keep hourly pay as a future configurable wage model; do not infer hours from timestamps in this release.
- Recognize the wage once when a shift is completed:
  - Dr 5000 – Driver wage expense
  - Cr 2100 – Driver payables
  - Journal line carries `driver_id`; source identifies the shift.
- A dedicated worker ledger table now records earnings, payments and advances, linked to immutable journals.
- `GET /api/v1/drivers/{driverId}/ledger` returns the worker statement with running payable balance.
- `POST /api/v1/drivers/{driverId}/ledger/payments` settles earned payable from cash/bank.
- `POST /api/v1/drivers/{driverId}/ledger/advances` supports two explicit advance types: `AGAINST_EARNED` (reduces payable) and `RECOVERABLE` (records a worker-advance asset in account 1300).
- Recoverable advances are automatically offset against subsequent earned wages, up to the amount earned, with a separate balanced journal and ledger entry. Generic expense records are not used for driver advances or wage settlement.
- Every source event has a tenant-scoped idempotency key backed by a database unique constraint and is committed atomically with its journal.
- The worker ledger is append-only at the MySQL layer: UPDATE and DELETE are rejected by triggers. Corrections must be compensating entries, not row edits.
- Posted financial events are immutable. Corrections use explicit reversal/adjustment entries.
- Tenant scope is always taken from the authenticated user; actor IDs are never accepted from request bodies.
- Worker statement shows dated transactions, source shift/reference, debit/credit effect, paid amount and running balance.
- VIEWER can read; ADMIN/OPERATOR can create permitted financial transactions, subject to existing authorization conventions.

## Completion and editing safety

The persisted recognition marker is `shifts.driver_wage_journal_entry_id`, unique per tenant. The ledger also has a generated `earning_shift_id` with a unique tenant+shift index, plus a composite foreign key tying the ledger row to the same tenant and driver as its shift. The finish operation, wage-ledger transaction and journal posting share one database transaction.

A completed shift whose wage has been recognized must not silently change its financial/source fields. Backend validation rejects edits; MySQL triggers independently protect the persisted shift against direct SQL changes to date, type, site, driver, equipment, wage, price, equipment operating cost, start/finish timestamps, journal link or completion status. Notes remain editable. Deletion is blocked. Corrections require a dedicated reversal/adjustment workflow.

Cancelling a completed, wage-recognized shift must not erase the earned wage. It must require a reversal/correction workflow. Reinstating a completed shift must not post a second earning.

## Advances: distinguish two meanings

Do not assume every advance is the same accounting event:
1. Advance against already-earned payable: Dr 2100 worker payable / Cr cash or bank.
2. Recoverable advance before earning (employee receivable): Dr a dedicated worker-advances asset account / Cr cash or bank; when wages are earned and offset, Dr 2100 / Cr worker-advances asset.

The chart now includes worker-advances asset account 1300. Recoverable advances are Dr 1300 / Cr 1000 cash or 1010 bank; wage recovery is Dr 2100 / Cr 1300. A worker payment against payable uses Dr 2100 / Cr 1000 cash or 1010 bank.

## Test requirements

- Completing a shift creates one balanced wage journal and one ledger earning.
- Repeated finish/retry cannot create a duplicate earning or journal.
- Concurrent completion requests cannot double-post.
- Cancel/reinstate of recognized completed shifts cannot duplicate or erase earnings.
- Recognized shift financial/source edits and deletion are rejected by both Backend and MySQL triggers.
- Direct SQL attempts to edit/delete worker-ledger rows are rejected; duplicate shift earnings are blocked by a unique generated-column index.
- Partial and full worker payments reduce payable correctly; overpayment is rejected.
- Advance types post to the correct accounts and reconcile to the worker subledger.
- Tenant isolation, role boundaries, idempotency, rollback atomicity and concurrent payment safety.
- Integration tests must run against MySQL/Testcontainers, not only H2.

## Non-goals for the first release

- Do not merge driver/worker advances into generic expenses.
- Do not implement hourly wage calculation until the tenant wage model is configurable and explicitly selected.
- Do not introduce microservices.
- Do not merge PRs or modify `main` without explicit approval.


## Concurrent-user safety review

The current branch uses row-level pessimistic locks for state-changing operations on shifts, drivers, equipment and expenses, plus optimistic `@Version` checks for stale edits. Database unique indexes remain the final guard for active driver/equipment reservations, journal posting keys, reversals, idempotency keys and one earning per shift.

Existing MySQL integration race tests cover:
- Starting a shift while another request changes driver availability.
- Starting a shift while another request moves equipment into maintenance.
- Two payments attempting to overpay the same expense.

Added worker-ledger race coverage:
- Two simultaneous payments against one driver's earned payable balance must serialize on the driver row; only one may spend the same available balance.

Known follow-up checks before declaring concurrency coverage complete:
- Same idempotency key submitted simultaneously for expense creation and driver-ledger writes should return a stable idempotent response rather than an unexpected server error.
- Concurrent duplicate journal posting and reversal requests should be exercised against MySQL.
- Cash/bank account balance policy must be explicitly defined before enforcing a non-negative balance. Posting rows are append-only, but this is not equivalent to a cash-balance floor.
- Inventory concurrency is out of scope until inventory issue/receipt workflows are implemented.

A CI-discovered trigger defect was corrected: the posted-shift trigger now permits the one-time `IN_PROGRESS -> COMPLETED` transition and first `finished_at` value, while keeping the financial/source snapshot immutable afterward.
