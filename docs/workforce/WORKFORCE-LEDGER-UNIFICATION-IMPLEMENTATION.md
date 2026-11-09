# Workforce Ledger Unification — Implementation Status

**Working branch:** `feature/workforce-ledger-unification`  
**Base:** `feature/wf12c-shift-workforce-identity`  
**Status:** Stage 1 implementation; compatibility period only. Not a completed cutover.

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

- Move shift earning and recoverable-advance recovery to Workforce member IDs.
- Move payment, advance, statement, and balance operations to Workforce member IDs.
- Move completed-shift correction reversal/replacement lookups to Workforce ledger entries while preserving the correction chain and existing downstream-consumption blockers.
- Add Workforce ledger API endpoints and migrate Flutter screens/clients without breaking the temporary legacy endpoints.
- Add nullable `journal_lines.workforce_member_id` for new Workforce-aware postings while retaining `driver_id` as historical provenance. Existing posted journal lines are protected by V23 immutability triggers, so V57 deliberately does not UPDATE/backfill them; their new Workforce column remains NULL.
- Extend `JournalLineRequest` and `JournalLine` to carry the canonical member ID. `JournalPostingService` resolves legacy driver-only requests through the tenant-scoped Driver mapping, validates an explicitly supplied member against the linked Driver, persists both dimensions, and includes both in idempotency and exact-reversal comparisons.
- Remove the legacy insert mirror only after no application path writes to the old ledger.

### Stage 3 — Reconciliation and retirement

- Reconcile row counts, transaction types, per-member payable balances, recoverable-advance balances, journal links, shift links, and correction chains per tenant.
- Verify no orphaned or unmapped legacy rows exist.
- Verify every client, report, scheduled task, and test has stopped querying the legacy table.
- Only then create a separate migration to remove legacy constraints/triggers and drop `driver_ledger_entries`.
- Do not combine the table drop with the initial backfill or application cutover.

## Reconciliation deliverable

- Added `docs/workforce/WORKFORCE-LEDGER-MIRROR-RECONCILIATION.md` with read-only checks for mapping completeness, row counts, exact field equality, per-member payable/recoverable balances, correction references, and trigger presence.
- The reconciliation pack is diagnostic only. It does not authorize data repair, application cutover, or legacy deletion.

## Stage 2 progress

- Added V57 and Workforce dimension support to journal-line entity/request/posting service.
- Legacy Driver-only requests remain accepted and resolve to the linked Workforce member; new requests can supply `workforceMemberId` directly.
- Historical journal rows are not rewritten because posted journal lines are immutable.
- Automated tests and MySQL migration execution are still pending; this code is not approved for production rollout.

## Validation status

The migration, entity/repository, mirror integration test, and read-only reconciliation pack have been committed to this branch. A follow-up safety correction gives the mirror its own primary-key sequence and uses `legacy_source_entry_id` for source traceability. The integration test currently exercises mirrored `ADJUSTMENT`, `PAYMENT`, and `RECOVERABLE_ADVANCE` movements; it does not yet prove actual shift `EARNING` or correction reversal/replacement flows. CI has not yet reported a build or test result for the current branch head; do not treat the stage as validated until the backend test suite and MySQL 8 migration tests pass.

No production database has been changed. No branch has been merged and `main` has not been modified.
