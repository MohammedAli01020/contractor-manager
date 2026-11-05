# WF-06 — Hourly Shift Compensation

## Status

Design checkpoint for hourly compensation. This phase extends the existing shift workflow; it does not introduce a separate time-clock or attendance aggregate.

Target branch: `feature/hourly-shift-compensation-06`  
Base branch: `feature/financial-foundation`  
Production migrations: not authorized or executed.  
`main`: unchanged.

## Product and domain decision

A shift remains the operational record for a worker's work assignment. Starting and finishing a shift are valid SaaS workflow primitives, but operational completion, wage calculation, and accounting recognition remain separate responsibilities.

WF-06 adds the `HOUR` compensation basis to the existing shift lifecycle:

1. **Start:** resolve the approved agreement effective on the shift date and freeze its hourly component, currency, unit, agreement/component identity, and hourly rate.
2. **During work:** keep the shift `IN_PROGRESS`. Do not estimate hours from elapsed wall-clock time and do not recognize a wage yet.
3. **Finish:** require an explicit actual-hours value from the user. The client previews the calculation, but the server recalculates it authoritatively.
4. **Recognize:** on the same successful finish transaction, save the final wage and create the existing wage journal, worker earning, and eligible advance recovery exactly once.
5. **After completion:** preserve the measured hours and wage snapshot. Any financial change must use the existing documented shift-correction workflow, extended to preserve/revise the hourly calculation snapshot consistently.

## Hourly calculation

- `compensationRate` is the frozen rate per hour.
- `actualHours` is a required positive decimal measurement at finish, not derived from `startedAt` and `finishedAt`.
- The accepted input precision is two decimal places (examples: 7.50, 8.25).
- `calculatedWage = Money.scale(compensationRate × actualHours)`, using the existing HALF_UP money policy and two decimal places.
- Missing, zero, and negative hours are invalid for HOUR completion. If no work occurred, cancel the shift with a reason; a zero hourly rate with positive hours remains supported by WF-05.
- A zero hourly rate with positive hours follows the already-approved WF-05 zero-wage behavior: the shift completes without zero-value financial entries or advance recovery.

## Compatibility

- Existing `SHIFT` agreements and legacy driver rates retain their current behavior.
- Historical shifts are never recalculated from current agreements or rates.
- `HOUR` must not be silently treated as `SHIFT`, and a missing actual-hours value must never be inferred from elapsed time.
- Existing positive-wage journal/ledger posting and zero-wage safeguards remain authoritative.
- `DAY`, `QUANTITY`, and `FIXED` remain out of scope for this phase.

## API and mobile behavior

- Add an explicit finish request carrying `version` and positive `actualHours` for HOUR shifts.
- Server-side validation is mandatory even if the mobile client validates first.
- The mobile finish flow for an hourly shift asks for actual hours, displays the frozen hourly rate and calculated total, and requires confirmation before submission.
- The existing bulk-finish flow must not send an hourly shift through the legacy finish request. Until it supports per-shift hour entry, hourly shifts are excluded from bulk completion and directed to individual completion.
- The response exposes the compensation basis, frozen hourly rate, actual hours, and final calculated wage so list/detail screens can explain the result.

## Implementation and verification checklist

1. [x] Update the shift entity, finish DTO/controller, resolver, service, response, and schema constraints for explicit hourly hours.
2. [x] Preserve the distinction between a pending hourly total and a completed zero-wage shift; pending hourly margin is returned as null and the mobile editor shows the hourly rate.
3. [x] Block the legacy completed-shift correction path for hourly shifts until an hourly-aware reversal/reposting workflow exists.
4. [x] Add backend integration coverage for HOUR agreement resolution, rate snapshotting, missing/zero/negative hours, decimal calculation/rounding, journal and earning creation. [ ] Extend with advance-recovery, concurrency, and SHIFT regression cases.
5. [x] Add mobile hourly-rate display, actual-hours finish input with preview/confirmation, request payload, and bulk-finish exclusion. [ ] Add dedicated Flutter widget tests.
6. [ ] Run the relevant backend and Flutter suites once after implementation, then open a reviewable PR targeting `feature/financial-foundation`.

## Explicit boundaries

No merge is performed by this phase without separate user approval. No changes are made to `main`, and no production migration is run.


## Completed SHIFT correction and zero wages

The approved WF-05 zero-wage rule also applies to audited correction of a completed SHIFT:
- Corrected wages may be zero, but never negative.
- A positive old wage is reversed through its posted journal and earning ledger row.
- A positive replacement wage is posted through a replacement journal and earning row.
- A zero side has no zero-value journal or driver-ledger row; the shift wage-journal pointer remains null.
- The audited correction record supports positive→positive, positive→zero, zero→positive, and zero→zero transitions.
- Database triggers enforce the conditional financial references. HOUR remains blocked from this SHIFT-only correction endpoint.
