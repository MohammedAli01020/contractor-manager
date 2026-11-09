# WF12C — Workforce Migration and Legacy Retirement Plan

**Status:** Discovery / architecture decision record — implementation not yet approved  
**Working branch:** `feature/wf12c-shift-workforce-identity`  
**Last documented:** 2026-11-08  
**Scope:** Workforce identity, shifts, worker ledger, advances, payments, corrections, accounting dimensions, APIs, Flutter, reports, tests, and Flyway migrations.

## 1. Purpose

Continue the migration from the legacy driver-centric model to a unified Workforce model without losing operational history, worker balances, auditability, or posted accounting data.

The goal is not to add a second ledger beside the old one. The goal is to make `workforce_member_id` the canonical identity for every person working with a contractor, migrate all responsibilities and historical references safely, and retire legacy structures only after proving that no live dependency remains.

## 2. Decisions

1. **Workforce is the canonical person identity.** A person has one `workforce_members` record regardless of whether they operate equipment, drive, supervise, engineer, perform technical work, or handle administration.
2. Keep person identity/job title separate from operational roles and assignments, compensation agreements, work-period type, and actual time worked.
3. New workforce, compensation, advance, payment, recovery, and ledger capabilities must be designed around `workforce_member_id`, not around `driver_id` or the `drivers` table.
4. Do not expand legacy dependencies for new features. Legacy structures may remain as a temporary compatibility/operational bridge while responsibilities are migrated.
5. Do not delete or rewrite historical shifts, ledger movements, journal entries, or posted journal lines to make the migration easier.
6. Posted accounting entries remain immutable. Corrections use compensating/reversal entries and idempotent posting keys.
7. Tenant isolation, transaction boundaries, concurrency/locking, and retry safety must be preserved.
8. No legacy table or column is approved for deletion by this document. Deletion requires a separate reviewed migration after the exit criteria in section 8 are met.
9. No changes to `main`, no PR merge, and no production migration are authorized by this document.

## 3. Findings verified in the current code review

The review was performed against the working branch listed above. It is a focused initial review, not yet a complete repository-wide reference inventory.

### 3.1 Workforce identity bridge exists

- `DriverService` creates a `WorkforceMember` through `WorkforceIdentityService` when a legacy driver record is created.
- Updating a driver synchronizes data to the linked Workforce identity.
- Driver responses are enriched from the linked Workforce member.
- `ShiftService.start()` copies `driver.getWorkforceMemberId()` into the new shift.
- Shift update also refreshes the Workforce member reference from the selected driver.
- The branch includes a migration named `V54__add_workforce_identity_to_shifts.sql` (verify exact migration filename/content before modifying it) intended to backfill and constrain the relationship between shifts, drivers, and Workforce members.

### 3.2 Shift operations still depend on the legacy driver record

The reviewed `ShiftService` still:
- Accepts `driverId` in shift requests.
- Loads and locks rows through `DriverRepository`.
- Checks active-shift conflicts using `driver_id`.
- Uses driver identity to serialize shift starts, wage posting, and advance recovery.
- Resolves compensation through the legacy driver object.
- Writes `driver_id` into accounting journal-line dimensions.
- Creates earnings and recovery movements in `driver_ledger_entries`.

Therefore, adding `workforce_member_id` to shifts is a migration bridge, not proof that the shift workflow has become Workforce-native.

### 3.3 Legacy ledger is structurally tied to drivers

The reviewed V20 migration creates `driver_ledger_entries` with a required `driver_id` and a foreign key to `drivers`. It also links ledger entries to shifts and journal entries.

The reviewed V22 migration adds protections including:
- A unique earning movement per shift.
- A composite foreign key connecting ledger movement, shift, tenant, and driver.
- Append-only protections for ledger updates/deletes.
- Protection for posted shift wage source data.

These protections are important invariants. Any replacement must preserve or deliberately supersede them with equivalent or stronger database-level guarantees; do not simply drop the constraints.

### 3.4 Public API is driver-oriented

The reviewed controllers expose:
- `/api/v1/drivers`
- `/api/v1/drivers/{driverId}/ledger`
- Driver-specific advance and payment endpoints.

Flutter and other clients may rely on these routes and DTOs. They must be included in the inventory and migrated with an explicit compatibility/versioning plan.

### 3.5 Accounting dimensions remain driver-oriented

The reviewed `JournalLine` entity contains an immutable `driver_id` dimension. Existing posted journal lines must remain intact. A Workforce accounting dimension must be introduced and backfilled/validated without changing the debit/credit amounts or rewriting posted entries.

## 4. Initial legacy dependency classification

| Legacy item | Current role | Decision |
|---|---|---|
| `drivers` | Operational profile, legacy API and locking/reference point | Keep temporarily; stop expanding it as the canonical person model |
| `shifts.driver_id` | Shift source identity, uniqueness/conflict checks, legacy API and ledger FK | Keep during transition; migrate operational reads/writes to Workforce before retirement |
| `shifts.workforce_member_id` | Workforce identity bridge on shifts | Canonical target; validate backfill and tenant-safe constraints |
| `driver_ledger_entries` | Earnings, advances, payments, adjustments/recoveries | Replace with a Workforce-native ledger only after complete reconciliation and compatibility plan |
| `journal_lines.driver_id` | Historical accounting dimension | Preserve historical values; add Workforce dimension and reconcile before any retirement |
| Driver-oriented APIs/DTOs | Compatibility surface for clients | Maintain until clients move; deprecate only with usage and migration evidence |
| Flutter driver screens and `driverId` payloads | Mobile operational workflow | Move to Workforce directory/member IDs; retain adapter only as long as needed |
| Driver-specific repositories/services/tests | Legacy dependencies and invariants | Inventory and migrate responsibility by responsibility |
| Account codes 2100 / 5000 | Existing payable/expense chart accounts | Do not rename or replace yet; assess all tenants, postings, reports, and account semantics first |

## 5. Target architecture

### 5.1 Canonical identity and operations

- `workforce_members`: canonical person identity/profile.
- Workforce role/assignment structures: operational capability and assignment to sites/equipment or other scopes.
- Compensation agreements: effective-dated compensation basis and rate, separate from identity.
- Work-period configuration: tenant-customizable morning/evening/night/flexible/no-fixed-period classifications.
- Actual work time: start/end/breaks/paid hours, including overnight spans; no implicit overtime or night premium.

The legacy driver concept, if retained temporarily, is an adapter/projection for driver-specific operations—not a second person identity.

### 5.2 Workforce Ledger

Design one append-only ledger keyed by at least:
- `tenant_id`
- `workforce_member_id`
- transaction type and date
- amount and balance effect
- idempotency/client request key
- source reference (shift, payment, advance, correction, or other source)
- linked journal entry where applicable
- actor and audit metadata

The exact schema and transaction taxonomy must be designed after inventorying all existing transaction types and consumers. Preserve the distinction between:
- earnings/payables,
- advances (including recoverable vs non-recoverable semantics),
- payments,
- advance recoveries,
- approved adjustments/corrections.

Do not assume all advances are expenses. Reconcile recoverable advances against the relevant asset account and payable movements.

### 5.3 Accounting

- Keep posted journal entries and lines immutable.
- Introduce a Workforce dimension for new postings.
- Backfill Workforce identity on historical lines only where the mapping is provably unambiguous; otherwise report and resolve exceptions without guessing.
- Preserve posting keys, balanced-entry validation, reversal idempotency, and tenant-scoped foreign keys.
- Review source types and reports before changing names such as driver payable/driver wage expense or account codes 2100/5000.

## 6. Implementation sequence

### Phase A — Complete dependency inventory (next task)

Search the entire repository, not only the initially reviewed files, for:
- `drivers`, `driver_id`, `driverId`
- `driver_ledger_entries`, `DriverLedgerEntry`, `DriverLedgerService`
- driver repositories, controllers, DTOs, permissions, audit entity types
- `workforce_members`, `workforce_member_id`, Workforce repositories/services
- advances, payments, recoveries, settlements, corrections, payroll/worker reports
- shift creation/update/start/finish/reactivation/correction paths
- journal-line dimensions, source types, account mappings
- Flutter routes, screens, BLoCs, models, API clients and tests
- platform-owner/admin surfaces, exports, reports and notifications
- Flyway migrations, database constraints/triggers, integration and concurrency tests

For each occurrence classify it as:
1. active runtime dependency,
2. historical schema/data dependency,
3. compatibility API/client dependency,
4. test/documentation-only reference,
5. safe-to-remove only after a named prerequisite.

Deliver a path-by-path inventory with the owner, risk, replacement, migration requirement, and retirement condition.

### Phase B — Design Workforce Ledger

Produce and review:
- transaction taxonomy and sign/balance semantics,
- table and tenant-safe FK design,
- idempotency and uniqueness rules,
- append-only and correction/reversal rules,
- concurrency/locking strategy,
- mapping from every legacy ledger transaction type,
- journal linkage and reconciliation queries,
- compatibility API plan,
- rollback and backup/restore plan.

Do not implement until the design is reviewed.

### Phase C — Implement Workforce-native financial workflows

Migrate shift earning posting, advances, payments, recoveries, and shift corrections to Workforce identity and ledger. Ensure a single source of truth; do not dual-write two independently authoritative ledgers.

### Phase D — Migrate clients and reporting

Move Flutter and web/admin clients, DTOs, API contracts, exports, statements, and reports to Workforce identity. Add regression tests for old and new flows during the compatibility period.

### Phase E — Backfill and reconcile

For every tenant:
- map each legacy driver to exactly one Workforce member;
- identify missing, duplicate, cross-tenant, or ambiguous mappings;
- migrate ledger movements with stable source/idempotency references;
- reconcile opening and closing balances per person and transaction type;
- reconcile totals to journal entries and relevant control accounts;
- prove shift-to-worker and journal-to-worker mappings;
- retain an exception report; never silently discard or guess.

### Phase F — Deprecate and retire legacy

Only after all exit criteria pass:
- stop legacy writes;
- observe that no legacy API/client or runtime consumer remains;
- remove compatibility adapters in a separate reviewed change;
- add a dedicated Flyway migration for schema retirement;
- take and verify a restorable backup before production rollout;
- deploy with rollback/forward-recovery procedures.

## 7. Required safety tests

At minimum:
- tenant isolation for every Workforce and ledger query/FK;
- concurrent shift start for the same Workforce member and equipment;
- concurrent earning posting and payment/advance recovery;
- idempotent retries for earnings, payments, advances and corrections;
- append-only ledger and immutable posted journals;
- one earning per shift;
- exact preservation of historical wage snapshots and compensation agreement history;
- correction/reversal chain correctness and balanced journals;
- legacy-to-Workforce balance reconciliation;
- API compatibility and Flutter integration/regression tests;
- migration tests on empty DB and representative populated DB;
- backup restore rehearsal before production migration.

## 8. Legacy deletion exit criteria

A legacy table/column is not deletable until all applicable conditions are evidenced:

1. All rows have been migrated or explicitly classified as retained historical records.
2. No missing, duplicate, cross-tenant, or ambiguous Workforce identity mappings remain unresolved.
3. Per-person ledger balances and aggregate control-account totals reconcile exactly (within the system's defined currency precision).
4. All historical journal entries remain unchanged and balanced.
5. Backend services, repositories, queries, scheduled jobs, reports, exports, and integrations no longer depend on the legacy structure.
6. Flutter and web clients no longer send or require legacy IDs.
7. API compatibility/deprecation period is complete and usage evidence shows no consumers.
8. Tests prove all new flows, concurrency and idempotency invariants.
9. A production backup has been created and its restore has been tested.
10. A separate deletion migration and rollback/forward-recovery plan have been reviewed and explicitly approved.

Until then, the legacy structure remains in place as a compatibility bridge. No deletion is authorized by this plan.

## 9. Next session — exact starting checklist

1. Confirm current branch and latest commit before editing.
2. Complete the repository-wide dependency inventory (Backend, Flutter, web/admin, migrations, tests, reports).
3. Trace every ledger transaction from API request through service, repository, journal posting, and response.
4. Build a transaction mapping matrix from `driver_ledger_entries` to the proposed Workforce Ledger.
5. Inspect the complete V53/V54 migration sequence and all relevant constraints/triggers; confirm exact filenames and backfill behavior.
6. Present findings and proposed schema/API design for approval before implementation.
7. Do not delete legacy tables/columns, change `main`, merge a PR, or modify posted financial history.

## 10. Evidence and limitations of this initial review

The initial code review inspected representative files including:
- `ShiftService`
- `DriverService`
- `DriverController`
- `DriverLedgerController`
- `JournalLine`
- `V20__create_driver_ledger_entries.sql`
- `V22__protect_posted_worker_ledger.sql`
- Workforce identity integration and the shift identity migration.

This is enough to establish that the current implementation is transitional and that the legacy driver ledger remains structurally active. It is **not** a complete usage count or a proof that every repository dependency has been found. Phase A must complete that inventory before schema or service retirement decisions.

---

**Decision summary:** Build forward on Workforce identity. Treat driver tables and IDs as temporary compatibility structures. First inventory, then design one Workforce Ledger, migrate and reconcile all financial/operational responsibilities, migrate clients, and only then consider deletion in a separate approved migration.


## 11. Active-shift Workforce identity guard — 2026-11-09

A focused implementation was added on the working branch after the initial review:

- Added Flyway V55 with a generated active_workforce_member_id and tenant-scoped unique constraint uq_shifts_one_active_workforce_member. Completed/cancelled shifts remain unrestricted because the generated value is NULL outside IN_PROGRESS.
- Shift start now locks in the documented order: legacy Driver projection, canonical WorkforceMember, then Equipment. It validates active-shift conflicts by both the Workforce identity and the legacy Driver during the compatibility period.
- Active-shift resource changes lock affected Driver rows in stable order, then their WorkforceMember rows in stable order, then Equipment rows; the requested Workforce identity is checked for an existing active shift.
- Database uniqueness violations for the new constraint map to the existing HTTP 409 ACTIVE_SHIFT_CONFLICT response.
- Added real-MySQL race tests for two starts using the same Workforce member with different equipment, and two starts using the same equipment with different Workforce members.

The existing uq_drivers_tenant_workforce_member constraint in V36 already prevents multiple Driver projections for one Workforce member within a tenant. V55 adds the canonical shift-level invariant rather than relying on that transitional uniqueness as the final guarantee.

**Verification status:** source changes committed to this feature branch; CI/integration execution has not yet been run from this editing session. Do not treat the new concurrency tests or migration as verified until the branch CI passes against MySQL. V6's driver constraint and legacy Driver references remain intentionally in place. No table/column deletion, production migration, main-branch change, or PR merge was performed.

## 12. Focused repository dependency audit — 2026-11-09

**Audit ref:** `feature/wf12c-shift-workforce-identity`, commit `81173e801515c4cce0a5ad8ce97eed01ff17c123`. This is a focused source/schema audit of the current branch, not yet a proof that every reference across every file, report, export, integration, or deployed client has been enumerated. No runtime code or database was changed by this audit.

### 12.1 Confirmed active runtime dependencies

| Area | Confirmed dependency | Classification | Retirement prerequisite |
|---|---|---|---|
| Shift creation/update | `ShiftService` accepts `driverId`, loads/locks `DriverRepository`, and copies the linked `workforceMemberId` to the shift. | Active compatibility + operational dependency | Change request/DTO/resource resolution and locking to canonical Workforce member IDs; preserve a temporary adapter only for old clients. |
| Shift completion and wage posting | `ShiftService` loads the Driver, resolves wage/compensation through the driver-linked identity, writes `driver_id` into journal dimensions, and inserts `driver_ledger_entries` earnings/recoveries. | Active financial dependency | Workforce-native earning/recovery posting and reconciliation, with immutable historical snapshots and equivalent idempotency/uniqueness/concurrency protections. |
| Active shift checks | Shift service and repository still check `driver_id` as well as `workforce_member_id`; V55 independently enforces the Workforce active-shift uniqueness key. | Active transition guard | Remove the Driver check only after all shift operations and clients use canonical Workforce IDs and equivalent tests pass. V55 must remain. |
| Shift reassignment/correction paths | Shift service still locks/loads legacy Driver rows and queries shifts by Driver ID in at least one active-shift lookup path. | Active operational dependency | Migrate all such paths to Workforce member identity and prove stable lock ordering and race behavior. |
| Worker ledger | `DriverLedgerService`, `DriverLedgerEntryRepository`, `DriverLedgerEntry` and `driver_ledger_entries` are live code paths for statements, payments, advances, earnings and advance recovery. Ledger rows are keyed by `driver_id`. | Active financial source of truth today | Design and implement one Workforce Ledger; migrate every transaction type, opening/closing balance and journal link; reconcile per person and tenant before cutover. |
| Journal dimensions | `JournalLine.driverId` maps to `journal_lines.driver_id`; `JournalPostingService` validates a supplied Driver ID through `DriverRepository` and persists it. | Historical accounting dimension + active posting validation | Add Workforce dimension for new postings, preserve posted historical values, prove mapping/reconciliation, then remove Driver validation only when new posting paths no longer send it. |
| Driver CRUD | `DriverController` exposes `/api/v1/drivers`; `DriverService` still owns driver create/update/delete and synchronizes the linked Workforce identity. | Active compatibility surface and legacy write path | Make Workforce CRUD authoritative; migrate any driver-specific operational attributes; deprecate route after mobile/web consumers move and usage is measured. |
| Driver ledger API | `DriverLedgerController` exposes `/api/v1/drivers/{driverId}/ledger` for statement, advance and payment operations. | Active financial API | Provide Workforce-member ledger API with equivalent authorization/idempotency semantics; keep an adapter only during a defined client migration window. |
| Live operational API | `LiveController` exposes `/drivers`; `LiveStatusService` still returns legacy Driver IDs because shift FKs and current mobile flow use them, while it consults Workforce assignments/roles to determine eligibility. | Compatibility projection, not yet a fully Workforce-native contract | Return canonical Workforce member IDs and update consumers; do not break current shift selection before the mobile/API contract is migrated together. |
| Flutter driver management | `drivers_screen.dart` and `driver_edit_dialog.dart` call `/drivers`, use legacy IDs and provide a separate driver-management surface. | Active client dependency | Consolidate create/edit/delete and navigation into Workforce directory/member flows; verify permissions and existing customer workflows before hiding the legacy screen. |
| Flutter shifts | `shift_editor_screen.dart` still stores/sends `driverId` and consumes the live candidate IDs as legacy driver IDs. | Active client/API compatibility | Switch the end-to-end contract to Workforce member ID (including edit-existing-shift behavior and conflict UX), with regression coverage. |
| Workforce identity bridge | `WorkforceIdentityService` still uses `DriverRepository` to find/lock a linked Driver; Driver create/update synchronizes Workforce identity. | Transitional identity adapter | Replace with explicit Workforce-to-operational-role/assignment relationships; preserve uniqueness and prevent duplicate person records. |

### 12.2 Schema and migration dependencies confirmed

- V20 creates `driver_ledger_entries` with required `driver_id`, a FK to `drivers`, a shift link and journal link, plus tenant-scoped idempotency/journal uniqueness.
- V22 adds the unique earning-per-shift key, a composite FK tying ledger movement to the exact shift/tenant/driver tuple, and append-only update/delete triggers. These are financial integrity controls, not disposable legacy clutter.
- V36 creates `workforce_members`, backfills/links each Driver to a Workforce member, makes `drivers.workforce_member_id` required and unique per tenant, and adds a tenant-safe FK. It does not retire Driver.
- V52 backfills Workforce role/assignment data from legacy Driver relationships; V53 creates/backfills operational Driver projections from Workforce assignments. This confirms that Driver is currently still used as an operational projection/adapter.
- V54 adds required `shifts.workforce_member_id` but deliberately retains `shifts.driver_id` and a composite FK ensuring both point to the same Workforce identity through the Driver projection.
- V55 adds an independent stored generated active-Workforce key and tenant-scoped unique index. It prevents duplicate active shifts by Workforce member but does not remove the Driver FK or Driver-based runtime dependencies.
- `JournalLine.driver_id` and ledger history are referenced by financial services. No migration in this sequence has moved ledger rows or journal dimensions to Workforce identity.

### 12.3 What is safe to conclude now

1. **Driver is not merely unused legacy data.** It remains an active operational compatibility projection and, more importantly, a live dependency in shift lifecycle and financial posting.
2. **The legacy driver ledger is still the active worker financial ledger.** Replacing/deleting it now would break statements, advances, payments, earnings/recoveries and journal linkage.
3. **Historical journal-line Driver dimensions must be preserved.** Do not null, rewrite, or drop `journal_lines.driver_id` while posted history and validation depend on it.
4. **V55 does not make Driver removable.** It adds the canonical Workforce uniqueness guarantee while the old guard and FK remain in place.
5. **No deletion is safe or approved at this point.** This audit did not execute SQL, modify code, drop constraints, delete rows, or change production.

### 12.4 Remaining inventory work before proposing deletion migrations

- Enumerate every repository occurrence of Driver IDs/entities across all backend modules, scheduled jobs, exports, reporting queries, security/authorization checks, audit metadata, and integration boundaries; map each to a concrete owner and replacement.
- Inspect every Flyway migration and trigger that references `drivers`, `driver_id`, `driver_ledger_entries`, and journal-line dimensions, including constraints added outside V20/V22/V36/V52-V55.
- Trace all ledger transaction types and balance semantics, including recoverable/non-recoverable advances, payments, adjustments, correction reversals, shift earnings and recoveries. Build a transaction-by-transaction migration matrix.
- Inventory Flutter navigation, all API calls/models/tests, platform-owner/admin web, exports and any external clients. Add usage telemetry or a measurable deprecation window before removing compatibility routes.
- Inspect every tenant's data with read-only mapping/duplicate/orphan/reconciliation queries. A single successful WF06 test database is not evidence that all customer tenants are clean.
- Design the Workforce Ledger and journal dimension changes separately for review. Require restore-tested backups, populated-database migration tests, tenant isolation, idempotency, concurrency, append-only and immutable-journal tests before cutover.

**Next step:** complete the exhaustive path-by-path inventory and transaction mapping matrix. Then present the Workforce Ledger schema/API cutover plan for explicit review. Do not start a deletion migration until those deliverables and the exit criteria in section 8 are satisfied.

## 13. Worker-ledger transaction inventory — source trace (2026-11-09)

**Reviewed branch head before this documentation update:** `1d1a912a538e6c5c9d638dcdd365a48cdf964ced`. This is a transaction-flow trace of the inspected production writers, repository, schema migrations, and integration tests. It is not yet a tenant-data reconciliation or an exhaustive inventory of every report/client consumer.

### 13.1 Existing transaction taxonomy and balance semantics

All legacy ledger rows use a positive `amount`; `balance_effect` carries the signed effect on earned/payable balance. The existing code also calculates recoverable-advance exposure separately.

| Legacy type | Creation path | Amount / balance semantics | Journal / source linkage | Workforce migration rule |
|---|---|---|---|---|
| `EARNING` | `ShiftService.finish()` when a completed shift has positive wage; also replacement earning in `ShiftCorrectionService`. | `amount > 0`, `balance_effect = +amount`. Zero-wage shifts intentionally create no row. | Wage journal; `shift_id`; unique original earning per shift, with a narrowly keyed exception for audited correction replacements. | Preserve every earning and its exact shift, journal, date, wage snapshot, and original/replacement chain. |
| `PAYMENT` | `DriverLedgerService.pay()`, `POST /api/v1/drivers/{driverId}/ledger/payments`. | `amount > 0`, `balance_effect = -amount`; rejected if greater than currently available payable balance. | Journal debits worker payable and credits selected active cash/bank account. Request fingerprint includes worker, date, amount, description, and payment account. | Move to Workforce-member payment API; preserve payment account, actor, date, request key, journal and exact balance effect. |
| `ADVANCE` | `DriverLedgerService.advance()` when API `advanceType=AGAINST_EARNED`; stored type is `ADVANCE`. | `amount > 0`, `balance_effect = -amount`; capped by available earned/payable balance. | Journal debits worker payable and credits selected cash/bank account. | Preserve the distinction that this is an advance against already-earned payable, not a recoverable asset advance. |
| `RECOVERABLE_ADVANCE` | `DriverLedgerService.advance()` when API `advanceType=RECOVERABLE`. | `amount > 0`, `balance_effect = 0`; excluded from earned/payable balance and tracked by a separate aggregate. | Journal debits the recoverable worker-advances asset account (system account code 1300 in the current chart) and credits selected cash/bank account. | Migrate as a separate recoverable-advance balance, not as payable reduction or wage expense. |
| `ADVANCE_RECOVERY` | `ShiftService.finish()` automatically recovers from positive wages when recoverable advance balance exists. | `amount > 0`, `balance_effect = -amount`; recovery is the lesser of recoverable balance and shift wage. | Journal debits worker payable and credits recoverable advances asset; linked to the earning shift. | Preserve each individual recovery, source shift, asset/payable journal lines, and remaining recoverable balance. Do not replace with a netted opening balance. |
| `CORRECTION_REVERSAL` | `ShiftCorrectionService` reverses the old positive earning before replacement. | `amount > 0`, `balance_effect = -old earning`. | Exact reversal journal linked to the original wage journal; correction audit row records old/reversed ledger IDs. V35 database trigger validates the correction envelope. | Preserve the reversal as its own immutable Workforce ledger movement and retain the complete correction chain. |
| `ADJUSTMENT` | Allowed by schema; observed in the ledger idempotency integration test as a manually seeded opening/test payable movement. No ordinary production writer for this type was identified in the inspected services. | Positive or negative effect is supported by the row model; amount must remain positive. | Test fixture posts a journal and links the adjustment row to it. | Do not assume it is unused or delete it. Search historical tenant data and all writers before defining a replacement adjustment workflow. |

### 13.2 Financial invariants verified in source

- V20 requires positive amount, non-zero balance effect, a tenant-scoped unique client request key, one ledger row per tenant/journal, and tenant-safe foreign keys to Driver, shift and journal.
- V21 explicitly permits zero balance effect only for `RECOVERABLE_ADVANCE`; it adds that transaction type and `ADVANCE_RECOVERY`.
- V22 adds a generated key and unique index for one original `EARNING` per shift, ties the ledger row to the exact tenant/shift/driver tuple, and prevents ledger UPDATE/DELETE at the database boundary.
- V30 adds `CORRECTION_REVERSAL`; V31 permits the specifically keyed replacement earning while preserving the original earning uniqueness rule.
- V35 validates correction reversal/replacement rows against an APPLYING correction, exact journal references, old earning, shift, driver, date, amount and balance effect.
- V23 makes posted journal entries and journal lines immutable. V17 defines `journal_lines.driver_id` as a nullable tenant-scoped FK to `drivers`; this is an independent legacy dependency beyond the worker ledger table.
- V25 reserves idempotency by `(tenant_id, operation_scope, request_key)` and request fingerprint in the same transaction as the financial write.
- Payment and advance service methods lock the Driver row before reading the balance and use READ_COMMITTED. Shift completion also locks the Driver before earning/recovery posting. A Workforce replacement must define one consistent lock owner/order to preserve this serialization.
- The ledger statement orders rows by `transaction_date, id` and computes a running balance from `balance_effect`; the aggregate payable balance is the sum of `balance_effect`. Recoverable advances are calculated separately as recoverable advances less recoveries.
- The shift correction workflow currently refuses correction if that shift already had an advance recovery, or if the worker had a later payment, earned-wage advance, or advance recovery. This prevents changing a wage after its payable has been consumed without a separate settlement process.
- The inspected correction request preserves the same driver for the old and new shift identity; a Workforce migration must not silently introduce worker-transfer correction semantics.

### 13.3 Current API and test evidence

- Ledger statement: `GET /api/v1/drivers/{driverId}/ledger` (ADMIN, OPERATOR, VIEWER).
- Advance: `POST /api/v1/drivers/{driverId}/ledger/advances` (ADMIN, OPERATOR).
- Payment: `POST /api/v1/drivers/{driverId}/ledger/payments` (ADMIN, OPERATOR).
- `DriverLedgerIdempotencyIntegrationTest` covers concurrent reuse of a payment key or recoverable-advance key across different drivers (one success, one conflict), identical payment retry, mismatched cash/bank account on retry, old rows without reservation records, and cross-operation key reuse.
- `ShiftCorrectionIntegrationTest` covers correction reversal/replacement, zero-wage correction, forged correction rows, correction retry, recovery/payment/earned-advance blockers, rollback on ledger posting failure, and hourly/quantity compensation correction cases.
- These tests establish important behavior in code, but do not establish that all live tenants' historical rows are valid or reconciled.

### 13.4 Workforce ledger cutover requirements

The replacement must preserve the ledger as a movement history, not just a cached balance. At minimum it must carry:
- tenant and canonical Workforce member;
- transaction type, effective transaction date, positive amount and signed balance effect;
- immutable description and actor/created timestamp;
- stable client idempotency key plus operation scope/fingerprint semantics;
- source reference and source type for shift earning, payment, advance, recovery, adjustment, and correction reversal/replacement;
- linked immutable journal entry and, where relevant, shift and correction references;
- unique original earning per shift, with an explicitly modeled audited correction replacement chain;
- append-only database enforcement and tenant-safe composite foreign keys;
- separate recoverable-advance balance semantics and a documented policy for allocation/recovery;
- a consistent Workforce-member lock strategy for concurrent earnings, payments, advances and recoveries.

Do not copy only `SUM(balance_effect)` into an opening row as the migration. The historical movements, their journal links and correction provenance must be migrated or explicitly retained and queryable. If a staged cutover needs opening balances, derive them from a reconciled immutable history and document the exact cutover boundary.

### 13.5 Outstanding questions before schema approval

1. Does any production tenant contain `ADJUSTMENT` rows, and what are their real business meanings and authorization sources?
2. Are there historical rows or external consumers that use the legacy `ADVANCE` / `RECOVERABLE_ADVANCE` names differently from the current API mapping?
3. Are there any reports, exports or external integrations reading `driver_ledger_entries` or `journal_lines.driver_id` directly?
4. How should Workforce-native journal dimensions coexist with immutable historical `driver_id` dimensions, especially when old Driver projections are eventually retired?
5. Should the new Workforce Ledger preserve old transaction type strings for historical continuity, or introduce a versioned canonical taxonomy with an explicit mapping table?
6. What is the controlled settlement workflow for correcting a shift after a later payment/advance/recovery has consumed its payable? Current behavior correctly blocks the correction; it must not be weakened during migration.

**Decision:** the next design deliverable is a reviewed Workforce Ledger schema/API and journal-dimension proposal, plus a read-only data reconciliation query pack. No migration, dual-write, data backfill, API implementation, or deletion is authorized by this inventory.

## 14. Workforce Ledger design proposal and WF06 evidence — 2026-11-09

The review proposal is documented in [WF12C-WORKFORCE-LEDGER-DESIGN-PROPOSAL.md](WF12C-WORKFORCE-LEDGER-DESIGN-PROPOSAL.md). It defines the target Workforce-native ledger, transaction semantics, journal dimensions, API/client migration, cutover, and legacy deletion boundary. It is a proposal only; it does not authorize schema changes, backfill, dual-write, API cutover, deletion, production migration, main changes, or PR merge.

### 14.1 WF06 tenant 8 read-only reconciliation evidence

Against the isolated `contractor_manager_wf06_test` database:

- 17 ledger rows were present: 15 `EARNING` and 2 `CORRECTION_REVERSAL`; net payable effect was 66,460.
- All 17 rows joined to a Driver and its linked Workforce member. Driver ID 4 mapped to Workforce member ID 7, confirming that migration must use the relationship, never numeric ID equality.
- The six inspected journal entries (1, 2, 3, 15, 16, 17) each had two lines and zero debit/credit difference.
- The two inspected correction chains had matching original, reversal, and replacement ledger/journal references. Reversal journal entries pointed to their respective original journal entries through `reversal_of_entry_id`.
- Historical journal lines carried `driver_id`; no Workforce journal dimension was present.
- This tenant sample contained no payment, advance, recoverable advance, advance recovery, or adjustment rows. This is not an all-tenant conclusion.
- These checks cover the named tenant and records only; they do not prove all journals or all tenants are reconciled.

**Next task:** complete the repository-wide dependency inventory and all-tenant read-only data inventory, resolve the open decisions in the proposal, and then present exact DDL/API contracts for approval.

## 15. Repository dependency inventory — source review on WF12C branch (2026-11-09)

This is a source-level inventory of the named files fetched from `feature/wf12c-shift-workforce-identity`. It expands the earlier focused audit but is not yet a mathematically exhaustive repository-wide reference scan. Search results from GitHub code search can be indexed against the default branch, so the branch-pinned file reads below are the evidence for these findings.

### 15.1 Financial write path — hard blockers to deleting Driver

| Area | Confirmed source | Current Driver dependency | Required Workforce replacement |
|---|---|---|---|
| Shift start/update | `ShiftService` | Accepts `driverId`, locks `Driver`, resolves compensation from Driver, writes `shifts.driver_id`; it also locks Workforce and checks Workforce active conflicts | Accept `workforceMemberId`; lock canonical member; resolve compensation from member/assignment; keep only a temporary projection if an operational capability is required |
| Shift finish | `ShiftService` | Locks Driver as serialization owner; posts wage journal lines with Driver dimension; inserts `DriverLedgerEntry(EARNING)`; calculates/recoverable advance balance by Driver and inserts `ADVANCE_RECOVERY` | Lock Workforce member as the sole financial serialization owner; post Workforce dimension; write Workforce ledger earning/recovery |
| Shift correction | `ShiftCorrectionService` | Locks Driver; reads ledger by Driver; tests later PAYMENT/ADVANCE/ADVANCE_RECOVERY by Driver; validates original journal lines' Driver dimension; writes reversal/replacement Driver ledger rows and Driver-dimension journal lines | Preserve exact correction chain and consumption blockers using Workforce member IDs and Workforce ledger references |
| Payments and advances | `DriverLedgerService` | Statement, payment, earned advance, recoverable advance, payable balance and recovery balance all keyed by Driver; payment/advance lock Driver; journal posting uses Driver dimension | Workforce-keyed ledger service, statements, payments and advances; lock Workforce member consistently |
| Journal validation | `JournalPostingService` | Injects `DriverRepository`, validates each non-null `driverId`, persists Driver dimension, compares it during posting immutability checks | Validate and persist `workforceMemberId` with tenant-safe FK; preserve old Driver dimension on historical posted lines |

**Implication:** removing only the Driver controller or UI is insufficient. Shift completion, correction, payment, advance, recovery, and journal posting all still depend on Driver as a runtime financial identity/lock owner.

### 15.2 API and mobile dependencies

| Surface | Confirmed source | Current contract | Replacement/removal condition |
|---|---|---|---|
| Shift API | `ShiftController`, `ShiftRequest`, `ShiftService` | `driverId` query/body/filter/default contract | Workforce member ID for all new clients and routes; compatibility adapter only during a bounded deprecation window |
| Driver ledger API | `DriverLedgerController` | `/api/v1/drivers/{driverId}/ledger`, `/advances`, `/payments` | Workforce-member routes delegate to one Workforce ledger service; remove old routes after client/usage evidence |
| Workforce candidate API | `LiveStatusService.workforceCandidates` | Candidates are derived from Workforce roles/assignments, but response `id` is still Driver ID because `shifts.driver_id` remains required | Return canonical Workforce member ID; separate capability/assignment metadata from person identity |
| Legacy live-driver API | `LiveStatusService.drivers` | Reads active Driver records and active shift rows keyed by Driver | Replace with Workforce directory/live status; remove when no client consumes it |
| Flutter shift editor | `shift_editor_screen.dart`, `shifts_api.dart`, `shift_model.dart` | Loads Workforce candidates but stores selected `_driverId`, uses candidate `id`, sends `driverId`, and falls back to `/live/drivers` for historical selection | Store/select `workforceMemberId`; remove fallback after all legacy assignments are reconciled |
| Flutter Drivers screen | `drivers_screen.dart` | Reads/writes `/drivers`, computes active state from shift `driverId`, edits/deactivates Driver records | Replace with unified Workforce directory and role/assignment operations; then remove Driver screen/API |

### 15.3 Canonical identity is not yet the operational source everywhere

The branch-pinned `Shift` entity has both:
- `driver_id` as a required column;
- `workforce_member_id` as the canonical identity column, documented in code as such.

`WorkforceIdentityService` still injects `DriverRepository`, creates Workforce identities from Driver records, synchronizes edits in both directions, and locks Driver before Workforce. `DriverService` still creates/updates Driver records and resolves their Workforce member. These are direct retirement blockers, not merely naming cleanup.

`LiveStatusService.workforceCandidates` already filters canonical Workforce assignments and roles, but it first loads active Driver rows and emits Driver IDs. This is a useful migration seam, not completion of the identity transition.

### 15.4 Accounting dimension dependency

`JournalLine` maps `driver_id`; `JournalPostingService` validates it using `DriverRepository` and includes it in immutable journal-line comparisons. The inspected source does not yet have a Workforce journal dimension. Historical journal rows must remain untouched; new postings need a Workforce dimension before the Driver dimension can stop being populated for new entries.

### 15.5 Source files reviewed in this pass

- Backend: `ShiftService.java`, `ShiftCorrectionService.java`, `DriverLedgerService.java`, `DriverService.java`, `DriverController.java`, `DriverLedgerController.java`, `LiveStatusService.java`, `Shift.java`, `ShiftController.java`, `ShiftRequest.java`, `JournalPostingService.java`, `JournalLine.java`, `WorkforceIdentityService.java`, `DriverLedgerEntry.java`, `DriverLedgerEntryRepository.java`, `DriverRepository.java`.
- Flutter: `shift_editor_screen.dart`, `shift_model.dart`, `shifts_api.dart`, `drivers_screen.dart`.

This pass did not yet close references in all DTOs, response mappers, repositories, reports, exports, audit entity types, tests/fixtures, scheduled jobs, external integrations, every migration, or every SQL query. Those remain explicit inventory work before any DDL is approved.

### 15.6 Ordered retirement work packages

1. **Identity/API contract:** make Workforce member ID the only person key in shift commands, queries, candidate responses, and client state.
2. **Operational projection decision:** decide whether a Driver capability/assignment projection remains at all. If not, move availability/status and any driver-only attributes to Workforce assignment/capability before removing the projection.
3. **Financial ledger:** implement Workforce-native earnings, payments, advances, recoveries, correction chain, idempotency and lock ownership.
4. **Accounting:** add Workforce journal dimension for new postings; preserve historical Driver dimensions.
5. **Clients and reports:** migrate Flutter, web/admin, reports, exports, and integrations; observe old-route usage before removal.
6. **Data migration:** reconcile every tenant and every legacy row; never equate Driver ID with Workforce member ID.
7. **Schema retirement:** only after all runtime references and data dependencies are gone, remove `shifts.driver_id`, Driver FKs/indexes/triggers, Driver APIs/entities/repositories, then `driver_ledger_entries` and finally `drivers` in dependency-safe migrations. Historical journal `driver_id` retention is a separate accounting/audit decision and may outlive the operational Driver table if retained as a non-FK legacy dimension or moved to a durable mapping record.

No code, schema, or data changes were made by this inventory.

## 16. Extended API, client, audit, and test inventory (2026-11-09)

This second pass inspected branch-pinned API DTOs, live endpoints, additional Flutter screens, and integration tests. It also confirmed the Workforce identity adapter still synchronizes Driver rows. It remains a source inventory, not a claim that every repository reference has been enumerated.

### 16.1 Additional response/query contracts

| File/surface | Confirmed legacy shape | Required transition |
|---|---|---|
| `ShiftResponse` | Exposes both `driverId` and `workforceMemberId`, plus `driverName` and `driverWage` | Make Workforce ID the primary identity; retain old response field only during explicit API deprecation. Consider neutral display naming only if product language requires it |
| `ShiftRepository` | Has both Driver-keyed and Workforce-keyed active-shift existence methods; also a Driver-keyed first-running-shift query | Remove Driver-keyed query methods after all callers and database guards have moved |
| `LiveController` | Exposes `/workforce-candidates` and legacy `/drivers` | Keep one Workforce-native candidate/live endpoint; remove legacy route after usage evidence |
| `LiveEquipmentResponse` | Embeds `driverName` as the person currently operating equipment | Decide whether API field becomes `workforceMemberName` or generic `operatorName`; do not couple equipment live state to Driver entity |
| Flutter `shifts_screen.dart` | Reads `shift.driverId` for running-driver sets and displays `driverName` / `driverWage` in details | Use Workforce member identity for active sets and neutral workforce display model |
| Flutter `bulk_finish_shifts_screen.dart` | Displays `driverName` while bulk finishing | Use workforce member display name; verify no hidden Driver-only API dependency in the bulk finish action |
| Flutter `driver_edit_dialog.dart` | Creates/updates via `/drivers`; contains `workerType`, `active`, and legacy driver-rate fields | Replace with unified Workforce member editor plus role/assignment and compensation agreement flows |

### 16.2 Identity synchronization adapter — direct retirement blocker

The inspected `WorkforceIdentityService` explicitly:
- injects `DriverRepository`;
- locks Driver before Workforce member to preserve a global lock order;
- synchronizes Workforce edits back to linked Driver fields;
- creates Workforce identity from Driver;
- synchronizes identity changes from Driver into Workforce.

This confirms that the current model is still bidirectional synchronization, not a completed canonical Workforce cutover. The final transition must reverse ownership: Workforce is the only identity write model, and any temporary operational projection must be derived from it, not synchronized as a second editable identity.

### 16.3 Audit/history dependencies

The inspected `AuditIntegrationTest` verifies `entityType=DRIVER` for driver rate changes and Driver search. The Workforce identity service already records canonical identity changes as `WORKFORCE_MEMBER`.

Required handling:
- new identity/profile audit events use `WORKFORCE_MEMBER`;
- old `DRIVER` audit events remain readable and searchable for historical records;
- provide a safe historical lookup/mapping from old Driver ID to Workforce member ID where the link is known;
- do not rewrite historical audit event IDs or pretend their original entity type was Workforce.

### 16.4 Integration test and fixture dependencies

The following tests were inspected and contain Driver-oriented setup/assertions:

| Test | Current dependency | Required replacement coverage |
|---|---|---|
| `AbstractShiftIntegrationTest` | Creates people through `POST /drivers`; shift helper sends `driverId`; availability helper updates Driver | Create Workforce member + assignment/capability; send Workforce ID |
| `ShiftIntegrationTest` | Reads `drivers.workforce_member_id`; asserts `driver_ledger_entries`; asserts Driver wage source; tests mismatch between Driver and Workforce IDs; uses Driver active unique key | Assert Workforce ledger, Workforce journal dimension, canonical member identity, and Workforce active unique key |
| `ShiftTenantIsolationIntegrationTest` | Driver-based shift filters, `/live/drivers`, and cross-tenant Driver IDs | Workforce-keyed filters and candidate endpoint with cross-tenant isolation |
| `ActiveShiftConcurrencyIntegrationTest` | Tests Driver availability/active-state triggers and Driver row races; also tests concurrent Workforce duplicate starts | Retain Workforce concurrency tests; replace Driver state-change races with Workforce assignment/capability state races |
| `AuditIntegrationTest` | Creates Driver and searches `entityType=DRIVER` | Test new Workforce audit events and historical Driver audit readability |
| `MasterDataIntegrationTest` | Driver CRUD, type/rate validation, search and optimistic version tests | Test unified Workforce identity CRUD separately from role/assignment and compensation agreement validation |
| `RoleAuthorizationIntegrationTest` | Driver endpoints and `driverId` shift authorization | Test Workforce endpoints, tenant/role authorization and assignment scope |

The concurrency tests must continue proving database-level uniqueness and race safety after the Driver guard is removed. Do not delete the old tests before equivalent Workforce tests exist and pass.

### 16.5 Migration and database dependency notes

- The current V55 migration adds the Workforce active-shift unique key but its own comment explicitly says to keep the legacy Driver guard during the compatibility period.
- Existing code-search results also identify V4 (initial `shifts.driver_id`), V6 (active Driver unique index), V7 (resource state-change triggers referencing active Driver shifts), and V8 (resource trigger/index adjustment) as historical migration touchpoints.
- Those migrations are immutable Flyway history. Retirement must be implemented as new forward-only migrations after a dependency inventory of current schema objects; never edit already-applied migration files.
- V54's branch-pinned file path was not confirmed in this pass; its behavior should be verified against the repository migration list before any migration design is finalized.

### 16.6 Updated exit gates

Before dropping `drivers`:
1. No active production code path requires `DriverRepository` for identity, locking, compensation, posting, live status, or corrections.
2. Shift request, response, filters, defaults, and Flutter state use Workforce member IDs.
3. All driver-facing screens have a Workforce replacement and no longer call `/drivers` or `/live/drivers`.
4. All new journal postings validate/use Workforce dimension; historical Driver dimensions remain auditable.
5. Workforce ledger handles earnings, payments, advances, recoveries, correction reversals/replacements, idempotency, concurrency, and tenant isolation.
6. Audit supports new Workforce identity events and historical Driver event lookup.
7. Reports, exports, scheduled tasks, integrations, and support/admin tools have been searched and migrated.
8. Backend, Flutter, migration, tenant-isolation, financial, correction, and concurrency suites pass on the target branch.
9. All-tenant data reconciliation and backup/restore rehearsal are signed off.

Only then may a separately approved migration remove Driver-specific operational tables and constraints. The historical accounting dimension in `journal_lines` is not automatically dropped with the operational Driver table.

No code, schema, or data changes were made by this inventory.

## 17. Domain model, availability, equipment lock, and legacy migration details (2026-11-09)

### 17.1 Driver still combines multiple concepts that must be separated

The inspected `Driver` entity contains:
- link to canonical `workforce_member_id`;
- `worker_type` (currently enum values include `DRIVER` and `WORKER`);
- operational `availability`;
- driver-specific rate fields and versioned tenant entity data.

This confirms the legacy table is not just a duplicate person table: it currently mixes identity link, worker classification, operational availability, and legacy compensation rates. The Workforce replacement must allocate these concerns explicitly:
- person identity/profile -> `workforce_members`;
- job title/worker classification -> workforce role/job-title model (not a hard-coded two-value Driver enum);
- active operational eligibility/availability -> assignment/capability or a dedicated operational status model with clear scope;
- compensation -> compensation agreements and their snapshots, not legacy rate columns.

### 17.2 Availability and equipment concurrency

- `DriverRequest` accepts Driver availability and worker type; `DriverResponse` projects them from Driver.
- `LiveDriverResponse` still carries Driver-oriented worker type and availability fields.
- `EquipmentRepository` documents lock ordering as equipment after Driver locks.
- `GlobalExceptionHandler` translates the old `ACTIVE_SHIFT_DRIVER_RUNNING` trigger and recognizes both the legacy active-Driver unique key and the Workforce active-member unique key.

The Workforce transition must define and test one global lock order for member/assignment and equipment rows. It must move the database trigger and error translation to Workforce semantics without weakening the equipment active-shift invariant. Do not simply remove the Driver lock before all shift start/finish/update, availability transitions, and equipment race tests are rewritten.

### 17.3 Legacy rate semantics

`ShiftDefaultsResponse` still exposes `driverWage`; Driver CRUD DTOs and Driver entity retain morning/evening/night rates. These are transitional rate inputs and must not become a second compensation authority. Confirm all tenants' rate usage and historical snapshot semantics before removing them; new rates should resolve from compensation agreements and period/shift configuration according to the approved compensation model.

### 17.4 Initial schema and historical migration constraints

- V3 creates `drivers` with tenant FK, tenant/id unique key, name index, worker type, and legacy morning/evening/night rates.
- V5 adds availability and unavailable reason and modifies historical shift uniqueness.
- V6/V7/V8 remain active-shift guard/trigger history identified in the previous section.
- Existing Flyway files are immutable migration history; use forward migrations only.

### 17.5 Additional source files reviewed

- Backend domain/API: `Driver.java`, `WorkerType.java`, `DriverAvailability.java`, `DriverRequest.java`, `DriverResponse.java`, `AuditEntityType.java`, `EquipmentRepository.java`, `GlobalExceptionHandler.java`, `ShiftDefaultsResponse.java`, `LiveDriverResponse.java`.
- Flutter/auth navigation: `auth_screens.dart` (already links to `WorkforceScreen`, so the new directory is integrated into the authenticated navigation; the separate legacy Drivers screen still exists).
- Migrations: V3 and V5 source reviewed; V6/V7/V8 were identified by branch-independent code search but must be fetched on the target branch before a detailed DDL plan.

### 17.6 Updated functional retirement gates

Before Driver entity/table removal, additionally require:
1. Worker type and job title are represented by Workforce role/job-title configuration without losing existing DRIVER/WORKER meaning.
2. Availability and unavailable reason have a Workforce-owned operational model, including the exact scope (person-wide vs assignment/site/equipment-specific).
3. Compensation resolution no longer reads legacy morning/evening/night rates for new shifts.
4. Equipment/Workforce lock ordering is documented and concurrency-tested; legacy trigger names and error mappings no longer carry Driver semantics.
5. The unified Workforce UI is the only people-management entry point; the old Drivers screen is retired without losing driver-specific operational controls.

No code, schema, or data changes were made by this inventory.
