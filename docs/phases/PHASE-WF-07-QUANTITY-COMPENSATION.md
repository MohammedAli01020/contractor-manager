# WF-07 — Quantity-Based Shift Compensation

## Status

Implementation checkpoint on `integration/stabilization-2026-11`. This phase extends the existing shift lifecycle and the approved compensation-agreement component model. It does not create a separate production/payroll aggregate.

- Production migrations are not authorized or executed.
- `main` is unchanged.
- No pull request is merged by this phase.

## Product and calculation rules

1. An approved agreement may contain one matching `QUANTITY` compensation component for a shift. Its unit code and rate are frozen on the shift at start.
2. A quantity shift starts as `IN_PROGRESS`; it does not estimate quantity and does not recognize wage at start.
3. On individual finish, the operator must enter the actual positive quantity in the agreement's unit. The app must not infer quantity from shift duration, equipment, or other operational data.
4. The accepted quantity precision is three decimal places. Zero, negative, missing, or over-precision values are invalid.
5. The backend is authoritative: `calculatedWage = Money.scale(compensationRate × actualQuantity)`, using HALF_UP and two decimal places.
6. A positive calculated wage uses the existing idempotent wage journal, worker earning, and eligible advance recovery transaction exactly once.
7. A zero unit rate with a positive measured quantity is valid and follows WF-05 zero-wage behavior: complete operationally, create no zero-value wage journal/earning/recovery rows, and leave recoverable advances outstanding.
8. Pending quantity shifts do not expose a misleading revenue-after-wage margin before quantity is measured.

## Mobile behavior

- The shift editor displays the frozen unit rate and the unit code from the agreement; the user cannot override the contractual rate.
- Individual finish asks for actual quantity, shows the unit and calculated total, and requires explicit confirmation.
- A zero calculated total displays the existing zero-wage warning before completion.
- Quantity shifts are excluded from bulk finish until bulk flow can collect and validate an independent measurement for each selected shift.
- Completed shift details show unit rate, actual quantity, and final wage.
- The existing SHIFT zero-wage warning/confirmation behavior remains unchanged.

## Accounting, audit, and correction boundaries

- The existing finish transaction posts only positive wage amounts; a zero result has no journal, EARNING, or ADVANCE_RECOVERY row.
- Completed actual measurements are immutable at the database layer.
- The existing correction endpoint remains SHIFT-only. HOUR and QUANTITY corrections stay blocked until each has a dedicated audited workflow that reverses the old financial entries and replaces the measurement, rate snapshot calculation, and financial postings atomically.
- No current agreement edit recalculates a started or completed shift.

## Verification checklist

- [x] Resolve and freeze a single QUANTITY agreement component and its unit/rate.
- [x] Add explicit actual quantity to the finish request and shift snapshot.
- [x] Calculate and validate quantity on the backend.
- [x] Add database constraints for quantity basis and completed-shift calculation.
- [x] Protect completed actual measurements from direct database updates.
- [x] Add backend integration tests for missing/zero/negative/over-precision quantity, calculation, positive posting once, and zero-rate financial suppression.
- [x] Add mobile quantity input, preview, finish payload, details, and bulk exclusion.
- [ ] Run the latest backend and Flutter CI after the mobile widget-test additions and resolve any failures.
- [x] Add focused Flutter widget tests for quantity finish, zero-result warning, and invalid measurement precision.

