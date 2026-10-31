# Driver and Worker Ledger – Design Notes

Status: incremental implementation on `feature/driver-worker-ledger`; backend CI passed for the first ledger/payment milestone, and advance milestone tests are pending.
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
- Every source event has a tenant-scoped idempotency key and is committed atomically with its journal.
- Posted financial events are immutable. Corrections use explicit reversal/adjustment entries.
- Tenant scope is always taken from the authenticated user; actor IDs are never accepted from request bodies.
- Worker statement shows dated transactions, source shift/reference, debit/credit effect, paid amount and running balance.
- VIEWER can read; ADMIN/OPERATOR can create permitted financial transactions, subject to existing authorization conventions.

## Completion and editing safety

Before posting wages from `finish()`, implement a persisted recognition marker/source relation with a unique tenant+shift constraint. The finish operation, wage-ledger transaction and journal posting must share one database transaction.

A completed shift whose wage has been recognized must not silently change driver, shift type or wage. The implementation must either reject those edits and require a dedicated correction workflow, or atomically reverse the prior recognition and post a replacement. Prefer rejection plus explicit correction in the first release to preserve auditability.

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
- Recognized shift wage/driver edits are rejected or corrected through a tested reversal workflow.
- Partial and full worker payments reduce payable correctly; overpayment is rejected.
- Advance types post to the correct accounts and reconcile to the worker subledger.
- Tenant isolation, role boundaries, idempotency, rollback atomicity and concurrent payment safety.
- Integration tests must run against MySQL/Testcontainers, not only H2.

## Non-goals for the first release

- Do not merge driver/worker advances into generic expenses.
- Do not implement hourly wage calculation until the tenant wage model is configurable and explicitly selected.
- Do not introduce microservices.
- Do not merge PRs or modify `main` without explicit approval.
