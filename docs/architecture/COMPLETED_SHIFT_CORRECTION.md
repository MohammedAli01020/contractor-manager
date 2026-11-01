# Completed Shift Correction — Design

## Status

Design checkpoint for the first audited correction workflow. This document does not change runtime behavior. The ordinary shift update endpoint and the database immutability trigger remain in force until the correction endpoint, ledger handling, migrations, and tests are implemented together.

## Goal

Allow an authorized administrator to correct a completed shift without rewriting or deleting its original accounting history. A correction must preserve the original journal, add an exact reversal, post the corrected wage, maintain the driver's ledger and advance-recovery balance, and retain a before/after audit record.

## Existing invariants confirmed in the current implementation

- `ShiftService.update` rejects changes to completed/posted shift source fields.
- The database trigger in V28 protects completed shift fields independently of the API.
- Completing a shift requires a strictly positive driver wage and a matching posted wage journal.
- Finishing a shift posts a `SHIFT_WAGE` journal and an `EARNING` driver-ledger entry.
- If recoverable advances exist, finishing can also post a `DRIVER_ADVANCE_RECOVERY` journal and an `ADVANCE_RECOVERY` ledger entry.
- Driver payments are posted against the driver's aggregate available balance. Payment ledger entries are not allocated to an individual shift.
- Shift finish and driver payment serialize on the driver row. The finish flow uses READ_COMMITTED so balance reads happen after the driver lock.

## Proposed user-facing behavior

- The ordinary Edit action remains available for safe non-financial fields already permitted by the current API (for example, notes).
- For a completed shift, show a separate **Correct completed shift** action to ADMIN only in the first release.
- Require a non-empty correction reason and the current shift version.
- Show the original values and proposed values before confirmation.
- Explain that the correction creates accounting and ledger history; it does not erase the original entries.
- Return a clear conflict message when the correction is unsafe or the version is stale.

## Correction scope

The first release may propose changes to shift date/type, site, equipment, shift price, driver wage, and notes. It must keep the original driver unchanged. Driver changes are deferred until both driver rows can be locked in stable order and both ledgers can be reconciled safely. All proposed values are validated with the same tenant ownership, active-resource, date, and money rules used by ordinary shift operations.

The corrected driver wage must remain strictly positive. The zero-wage completion protection must not be weakened.

## Financial posting model

For every accepted correction:

1. Lock the shift row and validate tenant, COMPLETED status, expected version, existing wage journal, and correction reason.
2. Lock the affected driver rows in ascending ID order (old and new driver when the driver changes). This serializes correction against payments and shift completions.
3. Verify the original wage journal and its journal lines.
4. If the original shift has an advance-recovery journal, verify its exact matching ledger entry and journal lines.
5. Post an exact reversal of the original wage journal. Never update or delete the original journal.
6. Post a correction wage journal for the corrected driver and corrected wage, using a unique correction posting key and a correction source reference.
7. Add immutable driver-ledger entries that reverse the old earning and recognize the corrected earning. Keep both entries linked to the correction and their respective journal entries.
8. The first release rejects correction when the original shift has an ADVANCE_RECOVERY entry. Reversing and recalculating recoveries is deferred until the recovery allocation rules have a separately tested design. The first release also rejects any later PAYMENT or ADVANCE_RECOVERY for the driver after shift completion.
9. Update the shift's current operational snapshot and point its wage-journal link to the new correction wage journal. The correction row stores the old journal, exact reversal journal, replacement journal, and old/reversed/replacement earning-ledger entry IDs before the shift update. The database trigger must permit this only when a matching correction record is in APPLYING state in the same tenant/shift; it must continue to reject ordinary updates.
10. Mark the correction APPLIED and record actor, reason, timestamp, old snapshot, new snapshot, original journal IDs, reversal journal IDs, replacement journal IDs, and ledger entry IDs in the same database transaction.

The journal posting service's exact-reversal validation must be reused. Original posted entries remain immutable and visible in the journal list and trial balance history.

## Conservative safety gate for driver payments and later recoveries

Because current driver payments are made against an aggregate driver balance and are not allocated to individual shifts, the system cannot reliably determine which portion of a later payment consumed the earnings from one particular shift.

Therefore, the first release must reject a correction if the affected old or new driver has a PAYMENT ledger entry created after the original shift was completed, or a later ADVANCE_RECOVERY entry that could depend on the original shift's recoverable-advance balance. This is intentionally conservative. It prevents silently changing a driver's already-paid balance.

Do not infer allocation from transaction date alone. Use persisted creation timestamps and shift completion time, and verify the exact repository query and timezone handling in implementation tests.

If the safety gate rejects a correction, the message must explain that a later payment or advance recovery exists and that a separately designed reconciliation workflow is required. Do not bypass this restriction by allowing a manual negative balance.

## Database protection

- Add a tenant-scoped completed-shift-correction table with immutable request identity, actor, reason, expected shift version, old/new snapshots, status, and financial references.
- Add a unique idempotency/request key scoped to tenant.
- Use a short APPLYING state only inside the correction transaction; the V28 trigger replacement may allow protected completed-shift columns to change only when a matching APPLYING correction row exists for that same tenant and shift.
- Extend the trigger's wage-journal validation so the current shift link may reference either the original canonical SHIFT_WAGE journal or a correction wage journal whose correction row belongs to the same tenant and shift and is being applied.
- Preserve tenant foreign-key/unique constraints and make correction history append-only at the application layer.
- Ensure rollback removes the APPLYING correction and every journal/ledger change if any later step fails.

## API and authorization

Proposed endpoint: `POST /api/v1/shifts/{id}/corrections`.

Request fields:
- `version`: required optimistic-lock version.
- `reason`: required, bounded text.
- corrected shift fields: same semantic fields as ShiftRequest.

First release authorization: ADMIN only. OPERATOR and VIEWER must receive 403. Tenant identity must come only from the authenticated principal, never from request data.

The endpoint must be idempotent under retries. A repeated identical request must return the original correction result; reusing the same key for different content must return 409.

## Concurrency and transaction rules

- Use one outer transaction for correction record, journal reversals, replacement journals, ledger entries, shift update, and audit.
- Use READ_COMMITTED for post-lock aggregate balance reads.
- Lock the shift first, then all affected driver rows in ascending ID order. Keep this lock order consistent with shift completion and payment flows.
- Re-read safety-gate ledger data after the driver locks.
- Use a unique posting key for each correction sub-operation.
- Database unique constraints remain the final defense against duplicate corrections and duplicate journal reversals.
- A stale version or a competing correction must return a business 409, not a generic server error.

## Flutter behavior

- Keep the normal edit form for allowed edits.
- Add a distinct correction flow for completed shifts, visible only to ADMIN.
- Require the reason and show a confirmation summary before submitting.
- Render API `message` for 4xx business conflicts, including 409, rather than a generic connection message.
- On success, refresh the shift detail/list and show the correction reference.
- Do not let a generic edit form silently become a correction form.

## Required tests before enabling the feature

### API/domain
- ADMIN can correct an eligible completed shift.
- OPERATOR and VIEWER are forbidden.
- Tenant isolation for shift, driver, journals, and correction record.
- Reason and expected version are mandatory.
- Stale version and duplicate/conflicting request keys are rejected correctly.
- Corrected wage is strictly positive; zero is rejected.
- Original wage journal remains unchanged and has exactly one reversal.
- Replacement wage journal is balanced and linked to the correction.
- Driver ledger reversal and replacement earning produce the expected final balance.
- Existing advance recovery is reversed and recomputed correctly when safe.
- A later PAYMENT blocks correction.
- A later ADVANCE_RECOVERY blocks correction.
- Driver identity remains unchanged in the first release; a driver-change correction is rejected.
- A failure at any posting/update step rolls back correction, journals, ledger, shift, and audit atomically.
- Concurrent payment versus correction and concurrent duplicate correction are safe across multiple application instances.

### Database
- Ordinary SQL/API updates to completed protected fields remain rejected.
- A matching APPLYING correction permits only the intended tenant/shift update.
- A correction for another tenant or shift cannot authorize the update.
- A completed shift cannot point to an unrelated, unposted, or wrong-source journal.
- V28's positive-wage and matching-journal requirements remain intact.

### Flutter
- Correction action visibility is ADMIN-only.
- Reason is mandatory.
- Proposed changes and impact confirmation are shown.
- 409 API messages are displayed accurately.
- Successful correction refreshes the view.
- The normal edit action does not bypass the correction endpoint.

## Explicit non-goals for the first release

- No deletion or mutation of original journal or driver-ledger entries.
- No correction when the shift itself has an advance recovery, or after a later payment/recovery for the driver.
- No automatic allocation or retroactive reallocation of historic driver payments.
- No bypass of completed-shift database immutability.
- No zero-wage completed shifts.
- No change to `main` or automatic PR merges.

## First-release scope tightening

To avoid making assumptions about aggregate driver payments or retroactively reallocating recoverable advances, the initial implementation is intentionally narrower than the eventual workflow:
- Driver identity cannot change.
- A shift with its own ADVANCE_RECOVERY cannot be corrected.
- A later PAYMENT or ADVANCE_RECOVERY for the driver blocks correction.
- Only the wage earning is reversed and replaced; original journals and ledger rows remain immutable.
- Shift notes are captured in the before/after correction snapshot.
- The correction record retains original, reversal, and replacement wage journal/earning-ledger references.

## Rollout sequence

1. Land the Flutter 409-message fix independently.
2. Implement correction persistence, API authorization, database trigger, financial postings, and backend integration tests.
3. Review the backend correction PR and its migration carefully.
4. Add the dedicated Flutter correction screen and tests.
5. Run CI and then test on the real Android device against the local backend.
6. Enable the workflow only after both backend and Flutter paths pass.
