# Workforce Ledger Unification — Implementation Status

**Working branch:** `feature/workforce-ledger-unification`  
**Base:** `feature/wf12c-shift-workforce-identity`  
**Status:** Stage 1 complete on branch; partial Stage 2 implementation. Compatibility period only; not a completed cutover.

## Objective

Make `workforce_members` the only operational identity for worker financial activity and retire the Driver-keyed ledger after a reconciled application cutover.

## Stage 1 delivered on this branch

- Added Flyway migration `V56__create_workforce_ledger_mirror.sql`.
- Added `workforce_ledger_entries`, keyed by `workforce_member_id` with tenant-safe member, shift, and journal foreign keys.
- Backfills existing rows from `driver_ledger_entries` using the tenant-scoped Driver-to-Workforce relationship. Numeric ID equality is never assumed.
- Preserves the legacy ledger row ID in `legacy_source_entry_id` and records `legacy_driver_id` as provenance only. The Workforce table owns an independent auto-increment primary-key sequence; sharing the legacy ID sequence would cause collisions during a future cutover.
- Adds a transactional `AFTER INSERT` compatibility trigger so new rows written through the current legacy service are mirrored into the Workforce ledger.
- Makes the Workforce ledger append-only at the database boundary.
- Adds a Workforce entity and repository for member-keyed statement and balance queries.
- Extends `DriverLedgerIdempotencyIntegrationTest` to verify that a legacy `ADJUSTMENT`, `PAYMENT`, and `RECOVERABLE_ADVANCE` are mirrored for the canonical Workforce member; payable balance remains unchanged by a recoverable advance, while its separate recoverable balance increases.

## Compatibility rules

1. `driver_ledger_entries` remains the current application write authority in this stage.
2. `workforce_ledger_entries` is a synchronized mirror, not yet the authoritative read/write store.
3. Do not dual-write from application services; the database trigger is the only mirror mechanism during this stage.
4. Do not update or delete either ledger movement. Corrections remain compensating entries.
5. Keep the old table, Driver APIs, and correction references until the application and all clients have completed the cutover.
6. `legacy_driver_id` and `legacy_source_entry_id` are historical provenance only; never use them for authorization, operational identity, or balance grouping.

## Required next stages

### Stage 2 — Application cutover

- Add the canonical Workforce dimension to new shift-earning and recoverable-advance-recovery journal lines while retaining `driver_id` provenance. The current shift services still write ledger movements to the legacy table; direct Workforce-ledger writes remain blocked until the full cutover.
- Add the canonical Workforce dimension to worker payment and advance journal lines. Payment, advance, statement, and balance operations still use the legacy Driver ledger and must be migrated in a later cutover.
- Completed-shift correction reversal and replacement journal lines now preserve the Workforce dimension. Correction ledger lookups and persisted correction links still target the legacy Driver ledger and are not yet cut over.
- Add Workforce ledger API endpoints and migrate Flutter screens/clients without breaking the temporary legacy endpoints. Read-only canonical endpoints are now implemented: `GET /api/v1/workforce/members/{memberId}/ledger` returns the statement with running payable balance, and `GET /api/v1/workforce/members/{memberId}/ledger/balance` returns payable and recoverable-advance balances separately. Both validate the member within the authenticated tenant, query only by `workforce_member_id`, and deliberately omit legacy Driver identifiers. Flutter migration and Workforce-native write endpoints remain outstanding.
- Journal-line Workforce dimension foundation is implemented: V57 adds nullable `workforce_member_id` with tenant-safe FK/index; `JournalLine`, `JournalLineRequest`, and `JournalPostingService` validate and persist it. Driver-only requests resolve through the tenant-scoped Driver mapping. Idempotent retries and exact reversals retain compatibility with immutable pre-V57 journal lines whose new dimension is NULL. V57 deliberately does not rewrite historical posted lines.
- Remove the legacy insert mirror only after no application path writes to the old ledger.

### Stage 3 — Reconciliation and retirement

- Reconcile row counts, transaction types, per-member payable balances, recoverable-advance balances, journal links, shift links, and correction chains per tenant.
- Verify no orphaned or unmapped legacy rows exist.
- Verify every client, report, scheduled task, and test has stopped querying the legacy table.
- Only then create a separate migration to remove legacy constraints/triggers and drop `driver_ledger_entries`.
- Do not combine the table drop with the initial backfill or application cutover.

## Partial Stage 2 deliverables

- Shift wage and recoverable-advance-recovery journals now carry both `driver_id` and `workforce_member_id`.
- Correction reversal lines copy both dimensions from the original line; replacement wage journals use the shift's canonical Workforce member.
- Worker payment and advance journals carry the linked Workforce member ID; cash/bank counterpart lines remain unassigned.
- The recoverable-advance integration test now verifies the mirror row by `legacy_source_entry_id`, confirms the canonical member and journal linkage, and asserts that the advance journal line carries `workforce_member_id`.
- Added `WorkforceLedgerBalanceResponse` and a read-only `/balance` endpoint that returns payable balance separately from recoverable advances. Integration assertions verify 400.00 payable and 75.00 recoverable after the fixture's earning, payment, and recoverable advance; cross-tenant access is checked for both statement and balance endpoints.
- Added `WorkforceLedgerQueryService`, `WorkforceLedgerEntryResponse`, and `WorkforceLedgerController` for tenant-scoped Workforce-native statement reads. The integration test now calls the new endpoint and checks statement row count, canonical member ID, running balance, and that `driverId` is not exposed. It also verifies a different tenant receives `404` when requesting that member's statement.
- Fixed the advance-posting path to retain the tenant-scoped locked Driver record before adding its Workforce dimension to the journal line.
- `ShiftCorrectionIntegrationTest` now asserts the original, reversal, and replacement wage journals preserve both dimensions.
- These changes do not switch ledger reads/writes to `workforce_ledger_entries`; the legacy table and V56 mirror remain in force.

## Reconciliation deliverable

- Added `docs/workforce/WORKFORCE-LEDGER-MIRROR-RECONCILIATION.md` with read-only checks for mapping completeness, row counts, exact field equality, per-member payable/recoverable balances, correction references, and trigger presence.
- The reconciliation pack is diagnostic only. It does not authorize data repair, application cutover, or legacy deletion.

## Validation status

The V56 mirror migration, Workforce entity/repository, reconciliation pack, V57 journal dimension, and partial Stage 2 journal tagging have been committed to this branch. The correction integration test has assertions for canonical dimensions on wage/correction journals, and the driver-ledger integration test now checks recoverable-advance mirror provenance and journal dimension. No backend build, migration execution, or integration test has been run in this environment. The newly added Workforce statement endpoint and its integration assertion therefore remain unverified until CI or a local MySQL 8 test run. CI has not reported a check run for the current branch head; do not treat the stage as validated until the backend test suite and MySQL 8 migration tests pass.

No production database has been changed. No branch has been merged and `main` has not been modified.
