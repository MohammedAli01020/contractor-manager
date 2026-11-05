# WF-08 — Audited Hourly & Quantity Shift Corrections

## Status

Design and implementation checkpoint created from `integration/stabilization-2026-11`.

- Base commit: `fddaf5bd14a5f822995c4d3e6469c62f056d29d8`.
- Working branch: `feature/wf08-hourly-quantity-corrections`.
- `main` is not changed.
- No pull request is merged.
- No production migration is executed or authorized by this phase.
- This document records the implementation contract; it is not a claim that WF-08 code is complete.

## Verified baseline

The existing completed-shift correction endpoint is:

`POST /api/v1/shifts/{id}/corrections`

It is ADMIN-only and runs through `ShiftCorrectionService.correct` in a transaction. It already:
- Locks the shift row and checks optimistic version and idempotency.
- Keeps the worker/driver identity unchanged.
- Verifies the original posted wage journal and matching earning-ledger row.
- Reverses the original journal instead of editing it.
- Posts a replacement wage journal only when the replacement wage is positive.
- Writes correction audit records in the same transaction.
- Rejects corrections where advances were recovered for the shift or where later payments/advances/recoveries make the worker balance unsafe to rewrite.

The service currently rejects `HOUR` and `QUANTITY` explicitly. The current correction request accepts a client-supplied `driverWage`; this is acceptable only for the existing SHIFT correction path and must not be trusted for measured compensation.

The shift already stores `compensationBasis`, frozen `compensationRate`, `compensationUnitCode`, `compensationCalculatedAmount`, `actualHours`, and `actualQuantity`. Migrations through V46 protect completed measurements at the database layer. The correction snapshot entity/table currently stores old/new wage and operational fields, but not old/new measured values or the frozen calculation inputs.

## Product and API contract

1. Keep the existing SHIFT correction request and behavior backward-compatible.
2. For HOUR, accept a new positive `actualHours` measurement; do not accept a client-authoritative wage.
3. For QUANTITY, accept a new positive `actualQuantity` measurement with the same unit frozen on the shift; do not accept a client-authoritative wage.
4. Preserve the existing measurement precision rules: hours use the established hours scale; quantity allows at most three decimal places. Confirm exact DB/API constraints against V42/V45 before implementation.
5. Calculate the replacement wage on the backend from the shift's frozen `compensationRate` and new measurement, using the same `Money.scale`/HALF_UP policy as shift completion.
6. Never reread the current compensation agreement to recalculate a started/completed shift. Agreement edits must not change the historical rate or unit.
7. A correction must change the measured value and the calculated wage together. The client must not be able to alter basis, rate, unit, agreement/component snapshot, or worker identity.
8. Keep the zero-wage policy: positive measurement with a zero frozen rate is valid; operational completion remains completed; no zero-value journal, EARNING, or recovery row is created. If the old wage was positive, reverse its posted journal and ledger earning. If the new wage is positive, post a replacement journal and EARNING row. Do not create a zero-value replacement.
9. Do not retroactively recover advances as a side effect of correction. Preserve existing correction safety blocks for shift-level recovery and subsequent worker balance activity; a separate settlement workflow is required if a balance has already been consumed.
10. Retain the existing note-only edit path separately from financial correction.

## Accounting and transaction invariants

- The original journal and its lines are immutable.
- A positive original wage is reversed exactly once, with exact line/account/worker matching and `reversal_of_entry_id`.
- A positive replacement wage is posted exactly once with a unique posting key.
- The worker ledger receives a matching `CORRECTION_REVERSAL` for the old earning and an `EARNING` for the new wage, as applicable.
- Old/new measurement, old/new calculated amount, old/new frozen rate and unit, old/new journal IDs, reason, actor, expected version, and idempotency key must be retained in an immutable correction snapshot.
- Shift row update, correction snapshot, journal reversal/replacement, worker ledger movements, and audit entries must commit or roll back together.
- A retry with the same key and identical payload returns the already-applied result; reusing a key with a different payload is a conflict.
- A second correction must be able to recognize the previous correction's replacement journal as the current original journal.
- Preserve tenant scoping and the existing tenant -> shift -> worker lock order. Review journal posting locks for deadlock risk before changing the order.
- Do not weaken V43/V44 correction guards or V46 completed-measurement protections. Extend their controlled correction authorization narrowly and fail closed for direct SQL updates.

## Implementation work breakdown

### WF-08A — Schema and immutable correction snapshot
- Add nullable old/new actual-hours and actual-quantity snapshot columns to `shift_corrections`.
- Add old/new compensation basis, rate, unit code, and calculated amount snapshot columns where needed to prove the calculation.
- Extend the correction database guard so only a valid in-transaction APPLYING correction can authorize a completed HOUR/QUANTITY measurement change; keep all other completed measurement updates blocked.
- Preserve existing trigger behavior for SHIFT corrections and reject cross-basis changes.
- Add integration tests for direct SQL mutation rejection and valid correction authorization.

### WF-08B — Backend correction workflow
- Add conditional request validation for HOUR/QUANTITY measurement fields without changing the SHIFT payload contract.
- Derive the new wage server-side from the frozen rate and measurement.
- Reuse the audited reversal/replacement posting mechanism, with explicit checks for zero-to-positive, positive-to-zero, and positive-to-positive transitions.
- Update the shift measurement/calculation snapshot only after the correction record and financial movements are prepared, inside the same transaction.
- Extend idempotency payload comparison to include measured values.
- Add integration tests for HOUR and QUANTITY, validation, tenant/role access, replay, rollback, sequential corrections, and journal/ledger consistency.

### WF-08C — Mobile correction UX
- Restore the completed-shift correction action for HOUR and QUANTITY only when the backend workflow is available.
- Show the old measurement, editable replacement measurement, frozen rate/unit, and previewed wage.
- Require a reason and explicit confirmation; display the zero-wage warning when the recalculated total is zero.
- Explain that posted entries will be reversed and replaced; never present this as editing an old journal.
- Keep SHIFT correction UI unchanged and keep the ordinary note-only edit separate.

### WF-08D — Verification and release gate
- Run focused backend integration tests and the full backend CI.
- Run Flutter 3.3.3 analysis/tests and the relevant widget tests.
- Review migration/trigger behavior on a disposable MySQL test database.
- Verify no production environment or database was targeted.
- Document CI run IDs, commit SHA, and remaining non-blocking warnings before opening any PR.

## Acceptance criteria

WF-08 is not complete until all of the following are true:
- HOUR and QUANTITY completed shifts can be corrected through an audited workflow.
- The new measurement is the source of truth for the new wage calculation.
- The frozen rate and unit remain unchanged.
- Journal reversal/replacement and worker-ledger movements reconcile exactly.
- Zero transitions create no zero-value financial rows.
- Invalid measurements, duplicate requests, unauthorized users, cross-tenant access, unsafe downstream settlements, and direct database edits are rejected.
- A forced failure leaves no partial correction, journal, ledger, shift, or audit state.
- Backend and Flutter CI pass on the documented supported versions.

