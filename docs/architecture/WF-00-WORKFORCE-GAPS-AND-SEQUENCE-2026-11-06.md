# WF-00 — Workforce gaps and implementation sequence (2026-11-06)

This addendum follows the source audit at integration SHA `a63e49b7a100f32af39d0cf5a20575cb512ba506`. It converts verified findings into a safe product sequence. It is planning only; it does not authorize implementation, PR merge, production migration, or main changes.

## Verified operational boundaries

### Identity and administration
- Backend has WorkforceMember identity and synchronizes legacy Driver identity.
- Backend exposes role listing/creation/deactivation and member assignment listing/creation/ending.
- Backend exposes compensation agreement listing, draft creation, approval, replacement approval and draft cancellation.
- No dedicated `mobile/lib/**/workforce/**` UI exists in the audited integration tree. Current mobile driver administration remains in legacy Drivers feature.
- There is no verified worker self-service/login feature in the audited tree.

### Compensation
- The enum contains SHIFT, HOUR, DAY, QUANTITY and FIXED.
- The inspected `ShiftCompensationResolver` explicitly resolves SHIFT, HOUR and QUANTITY. It does not resolve DAY or FIXED in the shift wage path.
- HOUR and QUANTITY require actual measurement at finish; their rate/unit comes from approved agreement and is snapshotted.
- Therefore DAY/FIXED are domain vocabulary, not a feature to advertise as supported until their business event, calculation, posting and tests are designed.

### Worker ledger and advances
- Ledger entries are still keyed by `driver_id`, and API routes remain `/api/v1/drivers/{driverId}/ledger`.
- Existing transaction types include shift earnings, payments, `ADVANCE`, `RECOVERABLE_ADVANCE`, and correction/reversal events.
- `AGAINST_EARNED` advance reduces the worker payable balance and is capped by earned available balance.
- `RECOVERABLE` advance debits a recoverable-worker-advances asset account and does not reduce payable balance at disbursement.
- Current controller exposes statement, advance and payment endpoints. No dedicated installment/recovery/settlement workflow or worker-facing statement screen was found in the audited tree.
- The existing ledger is a foundation, not a full payroll/settlement product. Any recovery from a future wage must be explicit, traceable, capped, and never counted twice.

## Recommended product sequence

1. **WF-08 acceptance gate:** complete actual Android acceptance for the stacked WF-08A/B/C branch. Do not use integration for HOUR/QUANTITY correction acceptance. Keep WF-08D blocked until user reports device results.
2. **WF-10 workforce mobile administration:** worker directory/profile, role and site assignment history, compensation agreement review/create/approval with clear permissions. Reuse backend endpoints; do not duplicate Driver identity.
3. **WF-11 worker ledger presentation:** worker account statement with opening/running balance, earnings by shift, advances, recoveries, payments and correction/reversal references. Read-only first; no new accounting engine.
4. **WF-12 advance lifecycle:** distinguish earned advance from recoverable advance in UI and policy. Add explicit recovery schedule/transactions only after approval of rules for deductions, caps, dates, cancellation and remaining receivable.
5. **WF-13 settlement/pay run:** flexible weekly/monthly/on-demand settlement, period cutoff, review, approval, payment allocation, reconciliation and closed-period protection. Do not force monthly-only close.
6. **WF-14 worker self-service:** optional worker login, limited access to own profile/assignments/statement/receipts, tenant isolation and revocation. Separate app security roles from job roles.
7. **Later:** DAY/FIXED compensation only when a concrete use case and event lifecycle are specified; offline support remains stage 2, with financial actions server-confirmed.

## Architecture decisions for future work

- Keep the current monolith and shared journal posting service; do not create a parallel worker ledger.
- Introduce workforce-oriented API naming gradually while maintaining Driver aliases and historical IDs until migration is safe.
- Keep role/assignment history effective-dated. A worker can hold more than one role, but shift selection must identify the relevant assignment where agreement scope depends on it.
- Do not equate job role (e.g. operator/technician) with application permission.
- Keep agreement changes prospective; shift compensation snapshots remain authoritative for historical work.
- Advances, recoveries, and settlement payments must be distinct source events and journaled exactly once.
- Mobile UX is Arabic-first and phone-first. Read-only statement UI should precede any complex settlement interaction.

## Explicit approval points before implementation

- Which staff categories can be paid through Workforce (driver, equipment operator, technician, general worker, supervisor, etc.).
- Whether a worker can have multiple simultaneous assignments and how a shift chooses one.
- Recovery policy for `RECOVERABLE` advances: installment amount/percentage, maximum deduction, priority, and handling of termination.
- Settlement cadence options, cutoff rules, approver roles, and payment-account policy.
- Worker login scope and whether access is enabled by tenant.
- DAY/FIXED meanings and their triggering work records.
- Tax, insurance, labor-law, and payroll compliance require specialist review; do not market legal compliance based only on this implementation.

## No action beyond documentation

No code, migration, PR, deployment, merge, or main-branch change is included in this addendum.
