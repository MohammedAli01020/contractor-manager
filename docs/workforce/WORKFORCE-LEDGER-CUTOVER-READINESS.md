# Workforce ledger cutover readiness

**Date:** 2026-11-09  
**Branch:** `feature/workforce-ledger-unification`  
**Status:** Source review completed; no writer cutover performed.

## Decision

The Workforce ledger is the intended canonical ledger, but it is currently a mirror/read model. `driver_ledger_entries` remains the sole application write authority. Keep that single-writer arrangement until every financial command, reader, correction path, and reconciliation check has moved together.

Do not introduce application dual-write. Do not disable the V56 trigger or drop either ledger as part of this readiness step.

## Current movement map

| Movement | Current write path | Journal behavior | Workforce mirror |
|---|---|---|---|
| Shift earning | `ShiftService.finish` writes `DriverLedgerEntry(EARNING)` | Posts `SHIFT_WAGE`; journal lines already include both Driver and Workforce dimensions | V56 AFTER INSERT trigger |
| Recoverable-advance recovery from shift wage | `ShiftService.finish` writes `ADVANCE_RECOVERY` after reading recoverable balance from Driver ledger | Posts `DRIVER_ADVANCE_RECOVERY` | V56 trigger |
| Payment of earned balance | `DriverLedgerService.pay` | Posts `DRIVER_PAYMENT`; payable line carries both identities | V56 trigger |
| Advance against earned pay | `DriverLedgerService.advance` type `AGAINST_EARNED` -> `ADVANCE` | Debits worker payable and credits cash/bank | V56 trigger |
| Recoverable cash advance | `DriverLedgerService.advance` type `RECOVERABLE` -> `RECOVERABLE_ADVANCE` | Debits recoverable advances asset and credits cash/bank | V56 trigger |
| Shift wage correction reversal | `ShiftCorrectionService.correct` writes `CORRECTION_REVERSAL` | Reversal journal preserves original dimensions | V56 trigger |
| Shift wage correction replacement | `ShiftCorrectionService.correct` writes `EARNING` with correction-specific idempotency key | Replacement journal carries both identities | V56 trigger |
| Statements/balance | Workforce query service reads `workforce_ledger_entries`; Driver endpoints read legacy ledger | No journal writes | Not applicable |

## Critical dependencies and risks

### 1. The lock identity is still Driver-first

- Shift finish locks the Shift, then Driver, then WorkforceMember before wage posting and recoverable-balance calculation.
- Driver payment and advance commands initialize accounts, then lock Driver, then WorkforceMember; aggregate reads occur after the lock under READ_COMMITTED.
- Correction locks the Shift, initializes accounts, then locks Driver and WorkforceMember.
- The Driver row currently serializes all balance-changing operations for a person across shift earnings, payments, and advances.

A cutover must define one consistent lock protocol based on tenant/account initialization, canonical WorkforceMember, then any other resources in a documented stable order. All earning, payment, advance, recovery, and correction commands must use it. Never retain a Driver lock as the only financial mutex once Driver is no longer canonical.

### 2. Idempotency is part of the financial contract

- Current payment and advance idempotency reservation uses namespace `DRIVER_LEDGER` and fingerprints containing `driverId`.
- Legacy ledger has a unique tenant/client-request-key constraint.
- Workforce ledger has its own unique tenant/client-request-key constraint.
- Shift earning and correction operations use stable posting keys and correction-specific ledger keys.

Before cutover, define a canonical namespace and fingerprint based on tenant + WorkforceMember + operation + normalized business payload. Preserve retry semantics for already-used legacy keys. A retry must return the original movement only when the complete request matches; otherwise return conflict. Ensure old and new endpoints cannot create two movements for the same logical request.

### 3. Corrections are coupled to both ledgers today

`ShiftCorrectionService`:
- Checks whether the original shift earning and its Workforce mirror exist and match.
- Blocks correction when a shift recovery exists, or later payment/advance/recovery movements exist for the Driver.
- Reverses the original journal and appends a legacy `CORRECTION_REVERSAL` row.
- Posts a replacement journal and appends a new legacy `EARNING` row.
- Stores both legacy and Workforce ledger entry IDs on the correction record.

The eventual Workforce-native correction must preserve immutable journal history and append-only movements, and replace the current mirror-verification logic with canonical movement verification. Historical legacy IDs should remain nullable provenance on correction records as long as audit/reporting needs them.

### 4. V56 mirror assumptions

V56:
- Backfills Workforce rows from Driver ledger using a tenant-scoped join.
- Enforces one mirror row per legacy source row, request key, and journal.
- Mirrors each subsequent legacy INSERT transactionally through a database trigger.
- Makes Workforce ledger append-only through UPDATE/DELETE triggers.

V58 allows the legacy provenance pair to be NULL for future Workforce-native rows, but it does not change the writer or disable the V56 trigger. The independent auto-increment sequence and nullable provenance are useful cutover preparation; they do not by themselves make concurrent dual writers safe.

## Required cutover sequence

1. **Canonical financial identity and locking:** introduce a tenant-scoped Workforce financial command boundary. Lock the WorkforceMember row as the shared mutex for every balance-affecting movement. Align shift finish, payment, advances, and corrections on the same lock order.
2. **Canonical idempotency:** move new request fingerprints and reservations to a Workforce-scoped namespace while preserving safe retries for legacy keys. Add concurrency tests proving two simultaneous requests cannot overdraw earned balance, double-recover advances, or double-post an earning.
3. **Move every writer in one controlled cutover:** shift earning, recovery, payment, both advance types, correction reversal, and correction replacement must append directly to Workforce ledger. Do not run legacy and Workforce application writers in parallel.
4. **Move all readers:** balances, statements, available earned pay, recoverable balances, correction guards, reports, and mobile screens must read canonical Workforce ledger. Legacy Driver endpoints may temporarily adapt to member IDs but must not read stale legacy balances after cutover.
5. **Reconcile and prove:** compare per-tenant/per-member totals by movement type, journal ID, shift ID, request key, and recoverable-advance balance. Verify each ledger row maps to the expected journal and each journal movement is represented exactly once. Include zero-wage shifts and corrections.
6. **Retire mirror:** only after all writes are Workforce-native and all reads are canonical, remove the V56 trigger in a forward migration. Retain legacy ledger rows as read-only audit/provenance until the product-owner-approved greenfield reset removes them.
7. **Retire Driver financial dependencies:** only after no service, endpoint, report, test, correction, or migration needs Driver ledger identity or balances may `driver_ledger_entries`, its repository/service, and then the Driver projection be retired.

## Required test matrix before step 3

- Payment racing with shift finish for the same member.
- Two payments racing against the same earned balance.
- Earned-pay advance racing with payment and shift finish.
- Two shift completions racing for one member (V55 active-shift uniqueness remains in force).
- Recoverable advance racing with shift recovery.
- Correction racing with payment/advance and with another correction retry.
- Same idempotency key + same payload returns the original result.
- Same idempotency key + different member or payload conflicts.
- Tenant A cannot lock, read, or post against Tenant B's member or ledger.
- Trigger failure rolls back the legacy insert and journal transaction during the compatibility phase.
- Canonical-native ledger insert with NULL legacy provenance succeeds after the eventual trigger removal, and append-only guards still reject update/delete.

## Explicit non-actions in this review

- No service writer was changed.
- No financial authority was switched.
- No dual-write was introduced.
- No Flyway migration or trigger removal was added.
- No legacy table or column was deleted.
- No main-branch change, PR merge, or deployment was performed.

## Immediate next implementation unit

Add focused integration tests around the shared WorkforceMember lock and financial race cases while the legacy writer remains authoritative. Then implement the canonical command boundary behind existing endpoints, initially delegating to the same single legacy writer. Only after the test matrix and full reader inventory are satisfied should a separate reviewed commit switch the writer to Workforce.

## Lock-protocol decision addendum (2026-11-09)

The earlier phrase "lock the WorkforceMember row as the shared mutex" is a target-state concept, not permission to change the lock order in one service. The source audit found existing workflows that acquire Driver before WorkforceMember, including financial commands and non-financial Driver-to-Workforce synchronization. Changing only financial commands to WorkforceMember-before-Driver would introduce a lock-order inversion and can deadlock with those synchronization paths.

### Safe transition protocol

Use a dedicated, tenant-scoped `workforce_financial_locks` row as the financial serialization mutex for each canonical member during the compatibility period:

1. Create exactly one mutex row for each WorkforceMember in the same transaction as member creation; backfill existing members with a unique `(tenant_id, workforce_member_id)` key before enabling the protocol.
2. Every balance-affecting command must lock that mutex row first: shift earning, shift recovery, payment, both advance types, correction reversal, and correction replacement. A command must resolve and tenant-check the canonical member before it can acquire the mutex.
3. After the mutex, retain the existing compatibility lock order for rows still in use: Driver, then WorkforceMember, then Equipment (and Shift/resource locks in one documented order for workflows that need them). No path may acquire the financial mutex after taking one of those row locks.
4. All command entry points must adopt the mutex in one coordinated release. Until that release, the old lock protocol remains authoritative; do not partially deploy the new protocol.
5. Keep non-financial Driver profile synchronization on its current Driver → WorkforceMember order. It does not acquire the financial mutex and must not mutate financial balances.
6. Add race tests that mix every pair of financial commands on the same member, plus tests for mutex-row creation/backfill, tenant isolation, and rollback. Verify no deadlocks/timeouts and exactly-once movement/journal outcomes.
7. After all legacy Driver financial dependencies are retired, the dedicated mutex may be removed in a later reviewed change if locking the canonical WorkforceMember row alone is proven safe across every remaining writer and profile workflow.

### Why not use WorkforceMember as the mutex immediately?

A lock is only a mutex when every competing path takes it before any conflicting lock. Today, Driver-first profile synchronization can hold Driver while waiting for WorkforceMember. If a newly changed financial path holds WorkforceMember while waiting for Driver, each transaction can wait for the other. A dedicated mutex row avoids this inversion while preserving the existing compatibility order beneath it.

### Updated implementation order

- First, add the mutex schema/backfill and repository/service primitives without changing existing writers.
- Next, wire all financial command entry points to acquire the mutex before any Driver/Workforce/Shift/Equipment row locks; do this as one coordinated unit, not one endpoint at a time.
- Then add pairwise concurrency coverage and run the complete MySQL financial integration suite.
- Only after the protocol is proven should the canonical idempotency migration and Workforce-native writer cutover proceed.
- Keep the V56 trigger and legacy ledger intact throughout this phase.

## Mutex foundation implementation status (2026-11-09)

- V63 creates `workforce_financial_locks` with one tenant-scoped unique row per canonical member and a composite foreign key to `workforce_members`.
- V63 backfills one mutex row for every member already present at migration time.
- `WorkforceIdentityService` now creates the mutex in the same transaction as a newly created canonical member, including members created from the legacy Driver onboarding path.
- `WorkforceFinancialLockService` and its pessimistic-write repository query expose the lock primitive for the future coordinated command cutover.
- Integration coverage asserts mutex creation for new canonical identities/onboarding and backfill for legacy identities migrated from Driver records.
- This is foundation only: no financial writer currently acquires the mutex, no writer or reader has switched, and the V56 mirror remains active. Do not treat the mutex as active protection until all financial entry points adopt it together.
- Backend CI for the current implementation is pending; report success only after the run completes.
