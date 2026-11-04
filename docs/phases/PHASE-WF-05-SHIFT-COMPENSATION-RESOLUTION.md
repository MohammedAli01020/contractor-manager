# WF-05 — Shift Compensation Resolution

## Goal

Resolve the approved workforce compensation agreement that applies to a shift date, calculate the shift-based wage, and persist an immutable calculation snapshot before any wage journal or driver-ledger posting.

This phase supports `SHIFT` compensation components only. Hour, day, quantity, and fixed-period calculation remain unsupported until shifts capture the corresponding measurable inputs.

## Current system constraints discovered

- A shift stores `driver_id`, `site_id`, `shift_date`, `shift_type`, and a legacy `driver_wage` amount.
- A driver links to one `workforce_member_id`.
- Assignments are effective-dated and may be site-specific or site-independent.
- Compensation agreements are effective-dated and can be general (no assignment) or assignment-specific.
- Finishing a shift currently posts the saved `driver_wage` to the journal and driver ledger in the same transaction.
- The agreement schema exists, but there is no migration that seeds agreements for existing drivers.

## Resolution rules

1. Resolve using the shift's business date (`shift_date`), tenant, worker member, and shift site.
2. Consider only approved agreements whose effective date range includes the shift date. A superseded agreement remains eligible only through its recorded `effective_to` date.
3. An assignment-specific agreement is eligible only when its assignment belongs to the same member and tenant, is effective on the shift date, and its site is either the shift site or site-independent.
4. General and assignment-specific agreements must never overlap. If corrupt or legacy data produces both as effective for the same shift, reject the resolution instead of silently preferring one.
5. More than one eligible assignment-specific agreement is an ambiguity error; never choose one arbitrarily.
6. Because the current shift record does not carry a workforce assignment ID, if multiple assignments are effective for the same member/site and an assignment-specific agreement could apply, reject rather than guessing which role the shift represents. A later UI/API enhancement can make the assignment explicit.
7. If there is no eligible assignment-specific agreement, use the eligible general agreement.
7. Within the selected agreement, select exactly one `SHIFT` component matching the shift type. Missing or duplicate matches are errors.
8. Reject unsupported bases for this phase. Do not infer hours, quantities, or units.
9. Persist the resolved agreement/component identifiers, agreement reference, source, basis, rate, shift type, currency, and calculated amount on the shift. The posting path must use this frozen snapshot, not re-resolve the agreement at finish time.
10. Once a shift has a wage snapshot or wage journal, agreement edits must not alter the shift or its posted amount.

## Legacy compatibility

There is no existing-data backfill for compensation agreements. Requiring an agreement immediately would prevent existing tenants from starting shifts.

Therefore:
- If the member has never had an approved compensation agreement, use the existing driver rate as an explicitly marked `LEGACY_DRIVER_RATE` source and snapshot it.
- If the member has at least one approved agreement but none is effective for the shift date, reject the shift. Do not silently fall back to a stale legacy rate across a contractual gap.
- Once a member is moved to agreements, new shifts must resolve through the agreement workflow.

This compatibility path is temporary and observable; it does not create a synthetic agreement or modify historical driver rates.

## Shift lifecycle

- On shift start: resolve and freeze the wage snapshot using the requested shift date and type.
- On update while still editable and before posting: if worker, site, date, or shift type changes, resolve again and replace the snapshot in the same transaction. If only notes or equipment details change, retain the snapshot.
- On finish: require a valid frozen wage snapshot and use its calculated amount for the existing idempotent journal and driver-ledger postings.
- On completed or financially posted shifts: preserve existing correction workflow; do not recompute from current agreements.
- On reinstate: preserve the original snapshot for an already-started shift; do not resolve a new agreement retroactively.

## Implemented foundation

- Created branch `feature/shift-compensation-resolution-05` from `feature/financial-foundation`.
- Added V40 snapshot columns and backfilled existing shifts from their already-stored `driver_wage` values.
- Added a resolver for general and assignment-specific agreements, with a member-row lock and locking reads to serialize wage resolution against agreement approval across application instances.
- Wired shift start and editable source changes to resolve and freeze the wage. The existing finish flow continues to post the frozen `driver_wage` amount.
- Exposed source and frozen agreement details in `ShiftResponse`.
- Updated Flutter shift creation to request the suggested wage using the selected site and shift date, and to refresh the suggestion when site, date, driver, or shift type changes.
- Added an integration assertion that the defaults endpoint previews the agreement rate for the selected site/date.
- Added integration coverage for agreement-based wages, immutable shift snapshots, and contractual date gaps. CI validation is still required.

## Data model direction

Add nullable snapshot columns to `shifts` for backward compatibility with existing rows:
- wage source (`AGREEMENT` or `LEGACY_DRIVER_RATE`)
- compensation agreement ID and component ID (nullable for legacy source)
- agreement reference and component code/name
- basis and currency code
- unit code (nullable for SHIFT)
- applied rate and calculated wage amount

The existing `driver_wage` remains the amount consumed by the current accounting posting path during rollout. The migration must backfill existing rows as legacy snapshots from their already-stored `driver_wage`; it must not recompute old wages from current driver rates.

## Required tests

- General agreement selected when no assignment-specific agreement applies.
- Assignment-specific agreement takes precedence over general agreement.
- Two different assignment-specific agreements may coexist, but a shift resolves only its matching active assignment.
- Inactive/out-of-date/wrong-site assignment is not eligible.
- Superseded agreement is usable only through `effective_to`.
- Draft/cancelled agreements never resolve.
- Missing or duplicate SHIFT component is rejected.
- HOUR/QUANTITY/DAY/FIXED component is rejected in WF-05 rather than guessed.
- No approved agreement uses legacy rate only when the member has no approved agreements at all.
- A contractual date gap after agreements exist is rejected.
- Updating an agreement after shift start does not change the saved shift wage.
- Shift update that changes the resolution dimensions refreshes the snapshot only while unposted.
- Finish posts the frozen amount once; retry cannot duplicate journal or ledger rows.
- Existing shifts retain their exact stored wage through migration.

## Out of scope

- Hourly, daily, quantity, and fixed-period calculations.
- Flutter UI for entering hours or quantities.
- Payroll periods, payslips, and bulk payroll.
- New accounting posting types or changes to driver advance recovery.
- Production migration execution.
