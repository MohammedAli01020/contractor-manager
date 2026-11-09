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
