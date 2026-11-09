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

### 2. Shift API and schema retain `driver_id` as an internal compatibility projection

The current source has these dependencies:

- `Shift` still maps `driver_id` as non-null, and the persistence constructor still stores the Driver projection ID.
- `ShiftRequest` accepts `workforceMemberId` as the canonical identity. A legacy `driverId` is optional; when both are supplied, the backend rejects mismatches.
- `ShiftController` accepts both IDs for legacy list filtering and canonical member filtering. Defaults lookup supports `workforceMemberId` without `driverId`.
- `ShiftService.start/update` resolve a tenant-scoped Driver projection internally from `workforceMemberId`; the new Flutter command sends the canonical member ID without `driverId`.
- Flutter still uses the projection in candidate/defaults compatibility paths and to render/edit historical data where necessary; it no longer sends the legacy ID in the new shift command.
- Active-shift uniqueness is already enforced for `workforce_member_id` by V55. The legacy Driver constraint remains an additional compatibility guard, not the canonical identity rule.

Therefore, the API command identity has moved to Workforce, but the shift aggregate and persistence schema are not yet Driver-independent. Do not remove `driver_id` until constructors, lifecycle services, candidate/live-status consumers, historical compatibility, and the legacy unique/FK constraints have been migrated.

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
| `shifts.driver_id` | Required Shift persistence/FK field and internal Driver projection; legacy list filter and live-status compatibility | Remove Driver projection from Shift persistence/lifecycle; make canonical member identity authoritative in every consumer; retain V55 member uniqueness and retire legacy FK/index only after migration |
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

- Added the tenant-admin mobile screen `WorkforceRoleManagementScreen`, reachable from the Workforce directory toolbar.
- Admins can create custom roles and turn shift eligibility on/off. The app sends the optimistic-lock `version` and refreshes after conflicts; non-admin users do not see the management entry point.
- The first Backend CI execution of the new role integration test passed Maven tests (7 tests, 0 failures/errors/skips) but its workflow guard still expected six tests. The guard was updated to seven; the rerun is pending.
- Flutter CI for the mobile role-management screen is running on Flutter 3.3.3.


### Implementation progress — canonical shift defaults and mobile loading

- `GET /api/v1/shifts/defaults` now accepts `workforceMemberId` independently of the legacy `driverId`. When both are supplied, the backend verifies that they identify the same tenant-scoped person.
- The legacy defaults overloads remain compatible; a canonical member can be resolved without a Driver projection for compensation-agreement lookup. The temporary legacy-rate fallback still requires the projection.
- Flutter now sends `workforceMemberId` when loading defaults. A follow-up review found that the editor's early-return and suggested-wage conditions still assumed `driverId`; both now recognize the canonical member ID.
- Added backend integration coverage for loading defaults by canonical member ID without sending `driverId`.
- The shift editor's save command and operational shift lifecycle still require the Driver projection today. This change is deliberately limited to defaults lookup and does not claim that shift creation or the full shift aggregate is Driver-independent.
- Existing V55 already enforces one active shift per tenant/Workforce member with a generated column and unique index. Keep it; do not create a duplicate uniqueness migration. V6's legacy active-driver constraint remains as a compatibility guard until the Driver dependency is retired.

Validation:
- Backend CI passed for `1cdd54232a40ee0a831a4ffbbe830d943befbd08`, including the canonical defaults integration test.
- Flutter CI passed for `b405ab312059d65ee58e238260e4d40f0c07eee5`.
- The branch remains isolated; no PR merge, main-branch change, or deployment was performed.


### Implementation progress — canonical-only mobile shift commands

- The Flutter shift editor now submits `workforceMemberId` without `driverId` when creating/updating a shift. The backend resolves the tenant-scoped legacy operational projection internally for now.
- This moves the mobile command contract to canonical Workforce identity while preserving old-client compatibility at the API boundary.
- The editor still uses the Driver projection ID internally for the current candidate picker and legacy defaults fallback. This is an acknowledged transitional UI dependency, not the identity sent by the new mobile command.
- Existing backend integration coverage `shiftCanBeStartedUsingCanonicalWorkforceMemberId` removes `driverId` from the request and verifies the canonical member and compatibility projection in the response.


### Implementation progress — canonical Workforce names in shift reads

- Shift response mapping now reads the display name from the tenant-scoped `WorkforceMember` record, not from `Driver`.
- Added `workforceMemberName` to shift responses. The old `driverName` response field remains as a compatibility alias populated from the canonical name.
- Flutter's shift model now prefers `workforceMemberName` and falls back to `driverName` for older server responses. Shift lists, bulk finish, correction summary, and editor fallback display the canonical person name.
- Added backend coverage asserting the canonical name and compatibility alias, plus mobile model coverage for canonical and legacy response shapes.

Validation status at documentation time:
- Backend CI for canonical response-name mapping and its integration assertion is running.
- Flutter CI for canonical-name parsing and shift UI updates is running.
- No schema migration was needed; this is a read-path ownership change only.


### Implementation progress — canonical shift command and response contract (2026-11-09)

- The shift request now accepts `workforceMemberId` without `driverId`; the backend resolves the tenant-scoped Driver projection internally. Legacy clients that send only `driverId` remain supported, and conflicting dual identifiers are rejected.
- Flutter sends the canonical member ID for create/update commands. The editor also loads defaults by canonical member ID, including the no-Driver-ID path.
- Shift responses now source the person's display name from `WorkforceMember` and expose `workforceMemberName`; `driverName` remains a compatibility alias populated from the same canonical identity.
- The latest backend validation for the canonical name response and test passed on `5da525cd`. Flutter validation for the canonical/legacy name parsing and display passed on `73b22df7`; canonical command payload validation passed on `6ac93498` and defaults loading passed on `b405ab31`.
- The branch head is `3f48b04f` (documentation-only follow-up). No schema deletion, ledger writer cutover, PR merge, `main` update, or deployment occurred.

**Next implementation boundary:** remove Driver from the shift aggregate's internal lifecycle, not merely from the mobile payload. Start with an explicit audit of `finish`, `cancel`, `reinstate`, correction, bulk-finish, and live-status code paths and their repository methods. Keep V55's canonical active-member uniqueness and resource lock ordering intact. The `driver_id` FK remains until that audit proves all paths no longer require the projection.


### Implementation progress — Workforce ledger read-path audit (2026-11-09)

The current mobile and API read paths are split as follows:

- The Flutter WorkforceLedgerScreen reads the canonical endpoints: GET /api/v1/workforce/members/{memberId}/ledger and GET /api/v1/workforce/members/{memberId}/ledger/balance.
- WorkforceLedgerQueryService reads only workforce_ledger_entries, tenant-scoped by canonical workforce_member_id. It calculates the statement running balance and reports payable and recoverable-advance balances separately.
- The Workforce ledger controller is therefore the current canonical mobile read surface. The inspected Flutter drivers directory screen does not render or call the legacy Driver ledger statement.
- The legacy GET /api/v1/drivers/{driverId}/ledger endpoint remains in DriverLedgerController, and DriverLedgerService.statement still reads driver_ledger_entries. Retain this route only as a compatibility surface until old-client/API usage is measured and an explicit deprecation window is approved.
- Workforce payment and advance commands are canonical at the HTTP boundary, but WorkforceLedgerCommandService still resolves workforce_member_id to a Driver projection and delegates to DriverLedgerService. The Driver ledger is still the actual write authority; the V56 trigger mirrors the insert into Workforce ledger.
- The command response is read back from the Workforce mirror by tenant and client request key. This depends on the mirror trigger remaining active during the compatibility phase.

**Cutover blockers confirmed by this audit:**

1. The native Workforce ledger writer does not yet exist; the Workforce command service is an adapter to the legacy writer.
2. Idempotency reservation/fingerprints remain in the Driver ledger namespace and include Driver identity. A canonical idempotency key design must be introduced before Driver retirement, with compatibility for retries that cross API routes.
3. The legacy Driver statement endpoint still exists. Before removing Driver ledger tables, enumerate external/API clients and deprecate or redirect this route without breaking supported clients.
4. Reconciliation must prove every legacy movement has exactly one mirror, with matching member, amount, balance effect, date, transaction type, journal and shift/correction references, plus separate recoverable balances.
5. V56 cannot be removed until native Workforce writes are active, every read is canonical, all supported legacy commands are routed safely, and rollback/reconciliation have been rehearsed.

No reader or writer authority was changed in this audit. No legacy route, table, column, trigger, or migration was removed.
