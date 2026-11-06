# WF-08B — Transactional measured-basis shift correction

Status: implementation branch created from the accepted WF-08A head; not merged.
Parent: `feature/wf08a-correction-snapshots` at `2581a1c1ff09e6ef528d28648d341d7bb3f2e435`.
PR strategy: stacked on WF-08A; do not retarget to integration until WF-08A is integrated.
Scope: enable completed-shift corrections for HOUR and QUANTITY without changing worker identity or weakening existing SHIFT correction behavior.

## Acceptance and safety boundary

WF-08A is technically accepted for progression to WF-08B based on focused trigger review and successful push + PR CI on the exact WF-08A head. This acceptance is not permission to merge PR #41, change `main`, or run production migrations.

## Business rules

- SHIFT correction remains backward compatible with the existing `driverWage` request and snapshot semantics.
- HOUR correction accepts `actualHours` only; QUANTITY correction accepts `actualQuantity` only. The irrelevant measurement must be absent and the relevant measurement must be strictly positive.
- The request must not accept a client-computed wage as the source of truth for HOUR/QUANTITY. The server calculates `ROUND(frozen compensation_rate * new measurement, 2)` using the existing shift-completion rounding policy.
- Keep the original compensation basis, contractual rate, unit, currency, wage source, agreement id/component/reference/code/name, and worker identity unchanged.
- Preserve the old measurement in the old snapshot and store the requested measurement in the new snapshot. Preserve NULL for the non-applicable measurement.
- Zero contractual rate is valid. Positive measurement plus zero rate yields a zero calculated wage; complete the correction without creating zero-value journal or worker-ledger entries. Existing advances remain outstanding; do not manufacture zero-value settlements.
- Positive old wage: reverse the currently linked posted wage journal and reverse its EARNING ledger effect. Positive new wage: post one replacement wage journal and one replacement EARNING ledger row.
- A zero old/new side has no journal/ledger reference. Never mutate or delete a posted journal.
- Preserve the existing safety refusal when an advance recovery exists for the shift or subsequent payment/advance/recovery activity makes the earning unsafe to rewrite. No implicit settlement of payments or advances.
- Idempotency replay with the same key and identical complete request returns the applied shift; same key with any different field conflicts.
- Lock order remains shift then worker/driver, matching existing completion/payment workflows. Keep all audit, shift, journal, ledger and correction-row changes in one database transaction.
- Do not change the worker identity in this release.

## Implementation workstreams

1. API contract
   - Add optional `actualHours` and `actualQuantity` request fields with precision/range validation.
   - Validate by the shift's persisted compensation basis after locking it.
   - For measured bases, reject `driverWage` as an authority; the server derives wage from frozen rate and measurement.
   - Include measurements in idempotency request equivalence.
2. Domain/service
   - Keep existing SHIFT path behavior unchanged.
   - Add HOUR/QUANTITY calculation and positive-measurement validation.
   - Populate the immutable old/new compensation snapshots on the correction row.
   - Reuse the established journal reversal/replacement and worker-ledger reversal/replacement patterns, preserving zero-side behavior and existing post-payment/recovery guards.
   - Ensure the shift update, correction status transition, journal postings, ledger rows and audit records commit or roll back together.
3. Database authorization
   - Add a forward-only V51 migration; do not edit V1–V50.
   - Extend the correction-insert validator to allow measured bases only when old/new basis, frozen rate/unit/currency, old/new measurements and calculated wage are exact and valid.
   - Enforce positive new measurement and exact server rounding in SQL.
   - Extend completed-shift protection so only the matching tenant/shift/version/APPLYING snapshot authorizes changing the applicable measurement and calculated amount; basis, rate, unit, currency, agreement identity, wage source and worker identity stay frozen.
   - Preserve immutable audit snapshots and existing V44/V46 financial-reference authorization.
   - Require matching reversal/replacement evidence for positive sides and NULL references for zero sides.
4. Tests
   - HOUR positive-to-positive correction: exact calculation, reversal, replacement, ledger, snapshots.
   - QUANTITY positive-to-positive correction: same guarantees.
   - Zero-rate HOUR and QUANTITY: correction applies; no zero journal/ledger rows; advance remains untouched.
   - Reject missing, zero, negative, excessive-precision and irrelevant measurements.
   - Reject client-forged wage, basis/rate/unit/currency/agreement/worker changes.
   - Reject direct SQL malformed APPLYING rows and unauthorized completed-shift measurement updates.
   - Verify idempotent replay and conflicting reuse of key.
   - Verify rollback when any journal/ledger/shift/audit step fails.
   - Verify concurrency: two corrections racing on one shift produce one applied correction; stale version conflicts.
   - Preserve SHIFT regression and existing payment/advance/recovery restrictions.
   - Verify tenant isolation for all new database paths.
5. CI/review
   - Require the new measured-basis integration tests to execute (not skip) on MySQL in CI.
   - Run backend CI on exact latest head for both push and PR events.
   - Review V51 interaction with V44, V46 and V48–V50 before requesting phase acceptance.

## Explicit exclusions

- No Flutter/UI work (WF-08C).
- No changes to worker identity.
- No changing historical compensation agreements or recalculating old shifts.
- No payment/advance settlement redesign.
- No production migration, PR merge, or `main` change.
