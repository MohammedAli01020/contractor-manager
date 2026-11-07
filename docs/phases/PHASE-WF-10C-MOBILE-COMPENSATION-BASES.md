# WF-10C — Mobile Compensation Basis Setup

## Status

- Branch: `feature/wf10c-compensation-basis-mobile`
- Base: `feature/wf10b-mobile-compensation-agreements`
- PR: draft review pending.
- No merge, `main` change, production migration, or production database operation is authorized.

## Goal

Make the compensation agreement editor accurately represent the compensation bases whose operational capture and wage calculation already exist. Compensation basis remains independent of workforce classification and job title.

## Mobile agreement editor

The mobile editor supports:

- **SHIFT — per shift:** one agreement with three explicit components for morning, evening, and night shift rates. The shift resolver selects the matching shift type and freezes the agreed rate on shift start.
- **HOUR — per hour:** one hourly rate. The shift remains in progress until the user enters actual hours at finish; the backend calculates and posts the wage.
- **QUANTITY — per unit:** one rate and a required unit code (for example TON or M3). The shift finish flow captures actual quantity and the backend calculates and posts the wage.

All rates are non-negative EGP amounts with at most two decimal places in the mobile form. Agreement effective dates, approval, overlap checks, assignment scope, replacement reason, and atomic replacement remain server-authoritative.

## Explicitly unavailable bases

- **DAY — per day:** not exposed yet. A daily agreement needs an explicit definition of a payable day, attendance/presence capture, treatment of multiple shifts on one date, partial days, absence, and duplicate earning prevention. It must not simply multiply a day rate by the number of shifts.
- **FIXED — fixed-period amount:** not exposed yet. The product must define the supported period (monthly/weekly/etc.), period boundaries, proration, join/leave dates, absence/leave handling, approval, and idempotent period posting. It must not be posted once per shift.

The backend enum and component schema accepting DAY/FIXED do not make either basis operationally supported. Keep them unavailable in the UI until their dedicated lifecycle and tests are complete.

## Financial and historical safeguards

- The backend remains authoritative for calculations and validation.
- Approved agreement changes do not recalculate started or completed shifts.
- Existing shift snapshots and posted journal/ledger entries remain unchanged.
- No historical backfill or synthetic agreement creation is part of this phase.
- Tenant isolation, assignment scope, effective dates, idempotency, and auditability remain mandatory.

## Verification checklist

- [ ] Flutter 3.3.3 analyzer and full mobile tests pass.
- [ ] Widget tests cover each editor basis, required inputs, precision, and generated request payload.
- [ ] Backend integration tests verify SHIFT morning/evening/night component selection and unchanged legacy compatibility.
- [ ] Existing HOUR and QUANTITY shift completion and posting tests remain green.
- [ ] Real-device test: create and approve each supported agreement, start matching shifts, finish with required measurements, and inspect completed shift details and ledger.
- [ ] Confirm failed approval leaves a visible draft and does not create duplicate agreements on retry.

## Out of scope

Daily attendance/pay calculation, fixed-period payroll, payroll runs/payslips, workforce advances/settlements, and production migrations.
