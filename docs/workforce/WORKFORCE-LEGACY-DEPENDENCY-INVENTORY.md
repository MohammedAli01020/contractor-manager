# Workforce legacy dependency inventory

**Date:** 2026-11-09  
**Working branch:** `feature/workforce-ledger-unification`  
**Status:** Historical source-level inventory; retirement direction is now approved by `ADR-WF-GREENFIELD-RESET-AND-LEGACY-RETIREMENT.md`.

## Purpose

Identify why the legacy `drivers` record, `driver_id`, and `driver_ledger_entries` still exist, then sequence their retirement without breaking shift operations, mobile clients, audit history, or posted financial records.

This is a repository source review. It does not inspect production tenant data, prove every runtime path, or authorize a schema change.

## Findings

### 1. `drivers` is not yet a disposable projection

The canonical person identity is linked through `drivers.workforce_member_id`, and DriverService reads WorkforceMember data when returning driver records. However, the legacy Driver record still owns operational data and workflows:

- `DriverService.create` creates the Workforce identity and the Driver operational record together.
- `DriverService.update` changes Driver fields and calls `WorkforceIdentityService.synchronizeFromDriver`; this means the legacy Driver update path still propagates identity changes toward Workforce.
- `DriverService.deactivate` also synchronizes the deactivation from Driver to Workforce.
- `ShiftService` still loads and locks Driver records for shift defaults/start/finish/correction flows, and uses the linked Workforce member for canonical identity and locking.
- `LiveStatusService` still depends on DriverRepository and groups running shifts by `Shift.getDriverId()`.
- The mobile `DriversScreen` still calls `/drivers` and `/shifts/running`, then derives running status from each shift's `driverId`.

**Conclusion:** Driver is currently a compatibility plus operational aggregate, not merely a read-only view. Before removing the table, move its remaining operational fields and invariants to an explicit Workforce operational model (or another justified resource model), migrate every consumer, and only then decide whether the projection can become a view or be removed.

### 2. Shift API and schema still require `driver_id`

The current source has these dependencies:

- `Shift` maps `driver_id` as non-null and its constructor accepts Driver ID.
- `ShiftRequest` requires `driverId`.
- `ShiftController` accepts `driverId` as a list filter.
- `ShiftService.list/defaults/start` accept Driver IDs and resolve tenant-scoped Driver rows.
- The mobile shift editor stores `_driverId`, sends `driverId` in the save payload, and uses legacy driver IDs to preserve editing of historical shifts.
- Existing shift uniqueness and active-resource constraints are keyed by `driver_id`; they must be redesigned around canonical Workforce identity before the column can be removed.

The shift row already has `workforce_member_id`, but coexistence does not make `driver_id` redundant while request validation, queries, constructors, uniqueness constraints, and clients still depend on it.

### 3. Financial ledger is only partially migrated

The implementation status document records that:

- `driver_ledger_entries` remains the active application write authority.
- `workforce_ledger_entries` is the mirrored canonical-member ledger.
- Workforce command endpoints currently resolve the tenant-scoped Driver projection and delegate to the existing Driver-ledger service.
- V56's database trigger is the only mirror mechanism; application dual-write is prohibited.
- Corrections and several shift/payment/advance workflows still use legacy ledger references.

Therefore, do not remove the old ledger, trigger, Driver financial service, or legacy correction references yet. The writer cutover needs a separately reviewed atomic design covering shift earning, payment, earned-pay advance, recoverable advance/recovery, correction reversal/replacement, idempotency, locking, journals, and balance reads.

### 4. Historical journal provenance must remain intact

V57 adds nullable `journal_lines.workforce_member_id` while retaining `driver_id`. Historical posted journal rows remain immutable and their Workforce dimension is intentionally NULL. Do not backfill or rewrite those posted lines merely to make the new model appear complete.

New postings should carry the canonical Workforce dimension and preserve Driver provenance only where required for compatibility/audit. Any eventual removal of a legacy journal dimension needs a separate accounting and reporting decision; it is not part of dropping the Driver table.

## Dependency and retirement matrix

| Legacy object/path | Current reason it remains | Required migration before retirement |
|---|---|---|
| `drivers` table | Operational fields, availability, rates, DriverService CRUD, shift locking and compatibility endpoints | Move required operational state to canonical Workforce/resource model; migrate all APIs, services, reports, tests and mobile screens; prove no runtime reads/writes |
| `shifts.driver_id` | Required Shift entity/request field, list/default/start flows, live status, mobile payloads, unique active-shift constraints | Make `workforce_member_id` the sole command/query identity; redesign uniqueness and locking; preserve legacy API compatibility during rollout; migrate historical references safely |
| `driver_ledger_entries` | Sole active financial writer and source for current legacy ledger operations/corrections | Approved single-writer cutover to Workforce ledger; migrate every reader/report/client; run tenant-level reconciliation and rollback rehearsal |
| V56 mirror trigger | Keeps Workforce ledger synchronized while legacy writer is authoritative | Remove only after no legacy writer exists and the Workforce writer is validated |
| `journal_lines.driver_id` | Historical provenance and legacy compatibility; posted lines are immutable | Keep historical provenance; decide separately whether new postings need it; do not mutate old posted journals |
| `legacy_driver_id`, `legacy_source_entry_id` | Provenance for mirrored ledger rows and source traceability | Retain for audit/reconciliation unless a later retention policy explicitly permits archival/removal |

## Recommended execution order

1. **Finish this inventory:** search backend, mobile, platform web, scheduled jobs, reports, migrations, integration tests, and API documentation for Driver/driverId/driver_ledger references. Classify each as identity, operational state, API compatibility, financial write, financial read, or historical provenance.
2. **Choose the operational ownership boundary:** define which fields remain on a Driver operational projection and which move to WorkforceMember/WorkforceAssignment or a separate operational profile. Do not blindly copy equipment/shift-specific state into person identity.
3. **Make new shift commands Workforce-native:** accept a canonical Workforce member ID, resolve any operational requirements server-side, and keep temporary legacy request compatibility only during a controlled client migration. Enforce one active shift per tenant/member with tenant-safe constraints and the agreed lock order.
4. **Migrate shift read paths and Flutter:** use Workforce member IDs in shift list, defaults, running/live status, and member screens; retain old response fields temporarily if older clients still need them.
5. **Design and approve the financial writer cutover separately:** one authoritative writer only; no application dual-write. Include all movement types, corrections, journals, idempotency, tenant isolation, concurrency, and reversal behavior.
6. **Migrate all financial reads and reports** to the canonical Workforce ledger and reconcile each tenant/member against legacy balances and journal links.
7. **Remove compatibility trigger and old tables/columns only in a later, separately reviewed migration**, after clean reconciliation, verified backups, tested rollback, and explicit product-owner approval.

## Supersession note\n\nThe original recommendation assumed live tenant data had to be preserved. The product owner has since clarified that current development data is disposable. Therefore, use the greenfield reset sequence in the new ADR instead of building a zero-downtime tenant migration platform. The dependency findings below remain valid and must still be resolved before dropping schema objects.\n\n## Explicit non-actions

This inventory does not authorize:

- Any Flyway migration or production database change.
- A new financial writer, dual-write, or changing the current write authority.
- Deleting `drivers`, `driver_ledger_entries`, `shifts.driver_id`, journal provenance, or legacy references.
- Removing the V56 trigger.
- Changing `main`, merging a PR, or deploying to production.

## Immediate next implementation unit

The next safe code unit is not a table drop or financial cutover. It is a focused **Workforce-native shift identity contract**: define request/response compatibility, member lookup/authorization, active-shift uniqueness, lock order, and test cases. Implement it only after the remaining shift consumers and constraints have been enumerated and reviewed.


## Implementation progress — 2026-11-09

The first mobile/API identity bridge is now implemented on
`feature/workforce-ledger-unification`:

- `LiveDriverResponse` exposes both the legacy operational `id` and
  canonical `workforceMemberId`.
- `/live/workforce-candidates` returns the canonical member ID alongside the
  Driver projection ID; the projection is still required by the current
  `shifts.driver_id` FK and operational services.
- Flutter shift editing reads `workforceMemberId` from shift responses and
  candidate rows, and submits it with the legacy `driverId`. The backend
  resolves and validates the pair; older clients can still submit only
  `driverId`.
- A backend integration test asserts that a workforce candidate exposes the
  canonical member ID corresponding to its Driver projection.
- Flutter CI now includes this branch so subsequent mobile changes receive
  the repository's Flutter 3.3.3 test and analysis checks.

**Important remaining limitation:** the current candidate query still starts
from active Driver projections. It does not yet make every Workforce member
eligible for a shift merely because the person exists in the directory.
Shift eligibility remains role/assignment-based, and the operational
projection dependency must be removed in a later shift-domain redesign.
This bridge does not authorize retiring `drivers` or `shifts.driver_id`.

Validation at the time of this note:
- The preceding Backend CI run for the shift request identity changes passed.
- Backend CI for the new live-candidate response and its integration test is
  running.
- Flutter CI for the mobile identity bridge is running.


### Implementation progress — configurable shift eligibility

- Added forward migration V62 with `workforce_roles.shift_eligible` (default false).
- Existing and newly provisioned `DRIVER`, `EQUIPMENT_OPERATOR`, and `WORKER` roles retain shift eligibility by default; other seeded/custom roles default to false.
- Role create requests can explicitly set `shiftEligible`; role responses expose it.
- Added an Admin-only, version-checked endpoint to change eligibility. Enabling an existing role also provisions the required legacy operational projection for its active assignments.
- Shift candidate selection now reads the role flag instead of a hard-coded role-code allowlist. Custom eligible roles map to the current legacy `WORKER` operational classification until the shift aggregate is fully Workforce-native.
- Added an integration test for enabling a custom role and seeing its assigned member appear in shift candidates.

This is an incremental compatibility bridge, not retirement of `drivers` or `shifts.driver_id`. The remaining identity and financial dependencies described above still apply.
