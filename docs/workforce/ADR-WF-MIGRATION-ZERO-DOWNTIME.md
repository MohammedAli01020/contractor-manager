# ADR — Zero-Downtime Legacy-to-Workforce Migration

- **Status:** Proposed architecture; requires product-owner approval before implementation
- **Date:** 2026-11-09
- **Working branch:** `feature/workforce-ledger-unification`
- **Scope:** Live migration of tenant-owned people, operational references, shifts, and financial records from legacy Driver/Worker structures to canonical Workforce structures
- **Related:** `WORKFORCE-LEGACY-DEPENDENCY-INVENTORY.md`, `LEGACY-DRIVER-LEDGER-DEPENDENCY-INVENTORY.md`

## 1. Decision summary

Use a tenant-scoped **Expand → Backfill → Synchronize → Reconcile → Cut over → Contract** process. Existing tenants must continue using the product while their data is prepared and validated.

Migration is resumable, idempotent, observable, and gated per tenant. A tenant is never switched merely because a backfill job completed. Cutover requires verified parity, tested application compatibility, and an explicit release/tenant approval.

This ADR does not authorize a production migration, a financial writer switch, trigger removal, or deletion of legacy schema.

## 2. Non-negotiable invariants

1. No tenant may read another tenant's records during backfill, validation, reconciliation, or cutover.
2. Preserve source identifiers as provenance; never assume IDs from different tables are equal.
3. Preserve business timestamps, effective dates, status, amounts, currency, journal links, source links, and audit actors where available.
4. Backfill retries must not create duplicate people, assignments, shifts, ledger movements, journal lines, or correction links.
5. A migration failure must be visible and resumable; it must not silently skip an unmapped or invalid source row.
6. Posted journals and posted financial movements remain immutable. Repair accounting history through approved compensating entries, never by rewriting posted history.
7. There is exactly one authoritative writer for each aggregate at any moment. Do not dual-write financial movements from application code.
8. Legacy schema remains available until all tenant cutover gates pass and a separately approved contract phase is completed.
9. Every phase is tenant-scoped and auditable, including who initiated, paused, resumed, approved, or cut over a tenant.

## 3. Migration control model

Introduce a migration-control aggregate (exact table/entity design is a later implementation task) with at least:

- Tenant ID and migration plan/version.
- Current state and current phase.
- Source/target object and last committed keyset checkpoint per object.
- Rows scanned, migrated, skipped (normally zero), and failed.
- Attempt count, start/end timestamps, heartbeat, and last error code.
- Reconciliation run ID, counts, monetary totals, and mismatch summary.
- Cutover mode/flag, approval actor/time, and application version.
- Audit history for state transitions and operator actions.

Suggested tenant states:

`NOT_STARTED → EXPANDING → BACKFILLING → SYNCHRONIZING → VALIDATING → READY → CUTOVER → STABILIZING → LEGACY_RETIRED`

Operational states may include `PAUSED` and `FAILED`. Resume must continue from the last committed checkpoint after revalidating the migration plan/version. A changed mapping or transform version requires an explicit new run/reconciliation rather than silently reusing an old checkpoint.

Do not make `LEGACY_RETIRED` reachable until the contract gates in section 9 are satisfied.

## 4. Phase A — Expand without breaking the running application

- Add target tables, nullable compatibility columns, indexes, and tenant-safe foreign keys in forward-only Flyway migrations.
- Deploy code that can understand both old and new representations while the legacy path remains authoritative.
- Keep existing API contracts working for older mobile clients; add canonical Workforce identifiers to new contracts without removing legacy fields yet.
- Add metrics and diagnostics before starting backfill.
- Test migrations against a production-like MySQL 8 snapshot and the supported application version.
- Do not rename/drop columns or tighten constraints until historical rows are mapped and the compatibility window has ended.

Flyway schema migrations are deployment-wide; tenant data migration is separately controlled per tenant. Do not pretend that a tenant flag can make a destructive global DDL migration tenant-specific.

## 5. Phase B — Resumable tenant-scoped backfill

Process each tenant independently and use deterministic keyset pagination (stable primary key ordering), not large unbounded offsets.

For every source row:

1. Read it under the tenant predicate.
2. Resolve the canonical target identity using an explicit mapping table or stable source provenance.
3. Validate required relationships and business invariants.
4. Insert/update only through an idempotent migration operation with a unique source identity, such as `(tenant_id, source_type, source_id, migration_version)`.
5. Commit the target row and checkpoint in the same database transaction where practical.
6. Record a structured failure with source identity and reason if validation fails; do not silently skip it.

Use bounded batches, configurable throttling, short transactions, and retry with backoff for transient failures. A process restart must resume safely. Re-running a completed batch must produce no additional target rows or financial effects.

Preserve original source IDs in dedicated provenance fields or a mapping table. Do not overload canonical primary keys with legacy IDs.

## 6. Phase C — Keep data synchronized while tenants remain live

The synchronization mechanism depends on the aggregate:

- **Financial ledger:** during the compatibility period, keep the current single legacy writer and its transactional V56 database mirror. The mirror is part of the source transaction; it is not application dual-write. Do not introduce a second independent writer.
- **Mutable identity/operational data:** use one authoritative command path and a transactional outbox/change log when asynchronous propagation is needed. The business-row change and outbox record must commit in the same transaction. Consumers must be idempotent and checkpointed.
- **Append-only records:** use stable source IDs and unique provenance constraints so retries cannot duplicate records.
- **Deletes/deactivation:** represent them as explicit tombstones/status transitions during the migration window; do not physically delete a source row before the target has consumed and validated the change.

If a transactional outbox is not justified for a specific aggregate, document the alternative capture mechanism and its consistency guarantees. Polling timestamps alone are not sufficient where updates can share timestamps or arrive late.

Never allow old and new application versions to independently write both ledgers. Compatibility endpoints must delegate to the same authoritative command service.

## 7. Phase D — Reconciliation gates

Run reconciliation read-only and tenant-scoped. A tenant cannot reach READY while any required check is missing or mismatched.

### Workforce identity and operations

- Source-to-target row counts by tenant and entity type.
- One-to-one identity mapping; no duplicate canonical person for one source identity.
- Role/assignment counts, effective dates, active/deactivated state, and site/project references.
- Shift counts and status totals; driver/workforce references; equipment links; active-shift uniqueness.
- Historical references resolve to the intended person and tenant.

### Financial ledger and journals

- Movement counts grouped by type and status.
- Amount totals grouped by currency, movement type, and accounting period.
- Earned/payable and recoverable-advance balances per member.
- Original/reversal/replacement correction chains and idempotency/source references.
- Journal-to-ledger links, debit/credit balance, account, tenant, and source-shift links.
- Provenance coverage and unmapped legacy rows.

Balance equality alone is insufficient. Compare movement-level detail and journal linkage. Any mismatch, orphan, duplicate, missing mapping, or unexplained difference blocks cutover.

Store reconciliation output, query/version identifiers, counts, totals, and mismatch details so the result can be reproduced and audited.

## 8. Phase E — Shadow reads and gradual cutover

1. Enable read-only shadow queries for a controlled tenant/cohort; compare results without changing user-visible responses.
2. Investigate discrepancies and repeat reconciliation.
3. Cut over reads for one approved tenant, keeping legacy data intact.
4. Observe errors, latency, support signals, and financial/report parity for a defined stabilization window.
5. Cut over commands only after the specific aggregate's writer has been implemented as a single authoritative Workforce-native path and concurrency tests pass.
6. Expand by tenant cohorts, not by an uncontrolled global flag.

Feature flags must be tenant-scoped, server-enforced, auditable, and default to the compatibility path. A client-supplied tenant ID or flag must never authorize cross-tenant access or select an unsafe writer.

Read cutover and write cutover are separate decisions. A read can move to Workforce while the legacy writer remains authoritative only if the mirror and reconciliation guarantees are intact.

## 9. Rollback and forward recovery

Before cutover: pause the worker, preserve checkpoints and errors, fix the mapping/transform, and resume. No source data should be lost.

After read cutover but before new writes: route reads back to the legacy path if the compatibility data is still complete; preserve diagnostics and investigate.

After Workforce-native writes begin: do **not** simply flip a flag back to the legacy writer. That would strand new movements and can create divergent balances. Use a rehearsed forward-recovery procedure or a controlled reverse synchronization/compensating workflow that preserves every journal and movement. The recovery direction must be designed and tested before enabling write cutover.

A database restore is a disaster-recovery action, not the normal rollback mechanism for a live tenant, because it can discard legitimate transactions created after the backup.

## 10. Contract phase — retire legacy objects separately

Legacy routes, columns, triggers, and tables are separate retirement decisions. For each object:

- Prove there are no active application, mobile, integration, report, export, scheduled-job, trigger, or operational dependencies.
- Complete tenant-level reconciliation and the stabilization window.
- Verify a restorable backup and rehearse the exact recovery procedure.
- Deploy compatibility deprecation before removal where external clients may exist.
- Remove one dependency group at a time in a separately reviewed forward migration.
- Retain historical provenance and immutable posted accounting history according to the audit/retention policy.

In particular, do not remove `drivers`, `shifts.driver_id`, `driver_ledger_entries`, V56 mirror triggers, correction legacy references, or journal provenance as one combined change.

## 11. Testing and operational readiness

Before any live tenant is enrolled, test:

- Backfill idempotency after process termination at every batch boundary.
- Retry after transient DB failure and after a partially completed tenant.
- Concurrent source updates while backfill is running.
- Missing/ambiguous mappings and tenant-isolation violations.
- Duplicate source events and out-of-order outbox delivery.
- Reconciliation detects deliberately injected missing, duplicate, wrong-tenant, wrong-amount, and broken-journal rows.
- Shadow-read parity and feature-flag isolation.
- Read rollback and post-write forward recovery.
- MySQL 8 behavior, Flyway upgrade from supported versions, and application/mobile compatibility.

Operational dashboards/alerts should include lag, last heartbeat, retry count, failed rows, reconciliation status, cutover cohort, and per-tenant error rate. Provide pause/resume controls with role authorization and audit logging.

## 12. Implementation sequence

1. Complete source dependency inventory and classify every legacy reference.
2. Approve the migration architecture and per-aggregate ownership/capture strategy.
3. Define the migration-control schema and state machine (no destructive DDL).
4. Build a dry-run planner and read-only reconciliation queries.
5. Build the idempotent tenant-scoped backfill framework on synthetic/test data.
6. Add synchronization for mutable aggregates where required.
7. Prove retry, concurrency, tenant isolation, and reconciliation tests.
8. Implement one aggregate cutover at a time, beginning with a reviewed Workforce-native command contract.
9. Enroll one test tenant, then one explicitly approved pilot tenant.
10. Retire each legacy dependency only after its own contract gates pass.

## 13. Explicit exclusions

This ADR does not authorize:

- Running backfill against production tenants.
- Changing the current financial write authority or adding application dual-write.
- Removing V56 or any other trigger.
- Dropping or renaming legacy tables/columns.
- Rewriting historical posted journals or ledger movements.
- Merging a pull request, changing `main`, or deploying to production.

The first implementation deliverable after approval should be a **dry-run migration planner plus read-only reconciliation pack**, not a destructive migration and not a financial writer switch.
