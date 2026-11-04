# Workforce Core: additive database migration proposal

**Status:** Draft technical proposal — not implemented  
**Date:** 2026-11-04  
**Depends on:** `commercial-erp-foundation-v1.md` and `workforce-legacy-dependency-inventory-v1.md`  
**Source baseline:** `feature/financial-foundation` at `e03e49b1fbe882e08dde702acb7a9ff3fcd05c84`

## 1. Goal and boundaries

Introduce a general tenant-scoped person identity and prepare existing driver operations to use it, while preserving all existing shift, journal, ledger, payment, advance, correction, and mobile behavior.

This proposal is additive. It does not implement full HR, payroll posting, worker transfer in completed shifts, or destructive legacy cleanup. It does not authorize code changes or database migrations by itself.

## 2. Proposed release sequence

### Release A — identity and legacy mapping

Create `workforce_members` as the canonical person identity table. Add a nullable link from `drivers` to `workforce_members`, backfill every existing driver, validate exact one-to-one mapping, then enforce NOT NULL and uniqueness once all tenants pass preflight.

Suggested `workforce_members` columns:
- `id` BIGINT auto-increment primary key
- `tenant_id` BIGINT NOT NULL
- `full_name` VARCHAR(150) NOT NULL
- `phone` VARCHAR(30) NULL
- `national_id` VARCHAR(30) NULL
- `notes` VARCHAR(500) NULL
- `active` BOOLEAN NOT NULL
- `version` BIGINT NOT NULL
- `created_at`, `updated_at` DATETIME(6) NOT NULL

Required keys:
- unique `(id, tenant_id)` for tenant-safe composite references
- FK `tenant_id -> tenants(id)`
- no unique constraint on national ID unless the product later defines whether it must be unique per company or globally

Add `drivers.workforce_member_id` with a tenant-safe composite FK `(workforce_member_id, tenant_id) -> workforce_members(id, tenant_id)` and unique `(tenant_id, workforce_member_id)` to enforce at most one driver operational profile per person. Backfill exactly one workforce member per driver, preserving every driver ID.

Identity ownership must be explicit:
- `workforce_members` becomes the canonical source for name, phone, national ID, notes, and person-level active state.
- Legacy `drivers` columns remain during the compatibility period; old driver APIs keep their contract but read/write the canonical identity through the service layer. Any temporary mirrored columns must have one documented write owner and a reconciliation check; avoid independent writes from two services.
- Driver-specific availability and shift-rate fields remain on `drivers` as operational-profile data until CompensationAgreement replaces rate lookup in a separately tested phase.

Preflight before enforcing constraints:
- count drivers with missing tenant IDs or invalid tenant references
- detect duplicate `(tenant_id, id)` anomalies
- verify no driver is linked to more than one workforce member
- verify every existing driver has exactly one mapping
- verify tenant ownership for every mapping
- compare canonical identity values with the source driver row after backfill

### Release B — assignments and operational profiles

Add `workforce_assignments` to support multiple concurrent or historical roles per person.

Suggested fields:
- `id`, `tenant_id`, `workforce_member_id`
- `role_code` (e.g. DRIVER, EQUIPMENT_OPERATOR, TECHNICIAN, SUPERVISOR, ENGINEER, ADMIN)
- `effective_from` DATE NOT NULL, `effective_to` DATE NULL
- optional `department_id`, `site_id`, `project_id`
- status/audit metadata and created/updated timestamps

Use tenant-safe composite FKs for workforce member and every optional tenant-owned reference. Require `effective_to IS NULL OR effective_to >= effective_from`. Prevent overlapping active periods for the same member/role in the service transaction by locking the workforce member; a simple unique key cannot enforce arbitrary date-range overlap. Do not assume one role per person.

Existing `drivers` remains the first operational profile. It keeps availability and current shift-operational rules. Do not make every workforce member eligible to operate equipment; eligibility comes from an appropriate assignment/profile and explicit permissions.

### Release C — one logical payable subledger

**Approved naming and compatibility decision (2026-11-04):** keep the existing physical table name `driver_ledger_entries` throughout the initial workforce transition. The preferred eventual name is `workforce_ledger_entries`, because the ledger will represent all workforce members, but do not rename it now. A later rename is optional and requires a separate dependency inventory, compatibility plan, and migration review. If the rename adds operational risk without meaningful value, retaining `driver_ledger_entries` is an acceptable final state.

Do not create a second authoritative payroll balance table.

Recommended compatibility direction:
- Keep the existing `driver_ledger_entries` physical table initially, but add `workforce_member_id` and backfill it through `drivers.workforce_member_id`. Preserve each row's primary key, tenant, `driver_id`, source/shift references, amount, movement type, timestamps, journal linkage, and idempotency identity exactly.
- Keep `driver_id` and all current constraints during the first mapping release.
- Add workforce-member indexes and a tenant-safe composite FK; validate every historical row maps to exactly one member before enforcing required constraints. Update statement/read services to aggregate by workforce member only after all historical entries are mapped and reconciliation passes.
- In a later, separately reviewed release, permit non-driver workforce movements by making the legacy `driver_id` nullable only after every legacy writer, trigger, query, and correction guard has been updated. Do not make `driver_id` nullable in the same migration that first adds `workforce_member_id`. Preserve the rule that shift-originated EARNING and shift-correction entries must still identify the exact legacy driver and shift.
- All new ledger rows must have exactly one workforce identity. A driver-originated row may carry both workforce member and legacy driver references; a generic worker row may carry only workforce member. Never dual-write one business event into two ledgers.

Required staged migration sequence:
1. Add `workforce_member_id` as nullable, with the correct tenant-aware index/FK strategy.
2. Backfill using `driver_ledger_entries.driver_id -> drivers.workforce_member_id`; do not infer identity by name or phone.
3. Reconcile row counts, unmapped rows, tenant ownership, balances by member, totals by movement type, and journal/source links.
4. Enforce `workforce_member_id` NOT NULL only when every legacy row has a verified mapping.
5. In a later release, generalize read/write services and only then consider nullable `driver_id` for non-driver workforce entries.

Before relaxing `driver_id`, revise and test:
- V20/V22/V31/V35 checks, generated columns, unique indexes and triggers
- `DriverLedgerEntry` entity nullability and all repository aggregate queries
- shift completion, shift correction, payments, advances, recoveries, reversals and idempotency
- lock order: tenant/account initialization, workforce-member lock, source document lock as applicable
- historical statements and balance reconciliation

### Release D — workforce accounting dimension

Add nullable `journal_lines.workforce_member_id` with a tenant-safe FK and index. Backfill it for every line whose `driver_id` maps to a workforce member. Keep `driver_id` unchanged for compatibility.

Extend journal request/posting validation so:
- any supplied workforce member belongs to the same tenant
- if both driver and workforce member are supplied, the pair must match the driver's mapped workforce member
- legacy callers supplying only driver ID continue to work and resolve the workforce member
- new generic worker postings can use workforce member without inventing a driver profile
- journal idempotency comparison includes the new dimension
- reversals reproduce the original dimensions exactly

Posted journal rows remain immutable. Never update existing posted entries to change their historical driver identity; only add the new dimension through a controlled migration backfill before exposing it to normal writes.

### Release E — compensation and payroll

Add effective-dated `compensation_agreements` only after workforce identity and payable subledger compatibility are proven.

The agreement must represent basis (SHIFT, HOUR, DAY, QUANTITY, FIXED, MIXED), rate/amount, currency, effective dates, applicable role/site/project where required, and approval/audit metadata. Define component-level terms for MIXED rather than storing an opaque formula.

Payroll periods/runs are calculation and approval records, not a second ledger. Draft/recalculation has no accounting effect. Approval recognizes only compensation sources not already recognized:
- shift earnings are included as previously recognized; never post again
- fixed salary and approved hourly/quantity amounts are recognized once on approval
- actual cash/bank payment remains a separate settlement event

### Physical table naming policy

The name `driver_ledger_entries` is retained during compatibility because schema objects, Java entities/repositories, native SQL, triggers, reports, migrations, correction guards, and integrations may depend on it. Do not perform a broad rename as part of the first workforce-dimension migration. After all dependencies have been inventoried and migrated, assess a separate rename to `workforce_ledger_entries`. Preserve the table's primary keys and all historical foreign-key relationships; if a rename is not operationally valuable, keep the legacy physical name and treat it as an internal compatibility detail.

## 3. Compatibility and accounting rules

- Preserve `shifts.driver_id NOT NULL` during the initial releases. The shift workflow currently means a driver/operator plus equipment; generalizing it to any employee is a separate operational product decision.
- Preserve the generated active-driver uniqueness rule and the MySQL availability triggers until a new role-specific resource-slot model is designed.
- Preserve shift wage snapshots and historical journal/ledger IDs.
- Keep system account codes 2100 and 5000 and current tenant chart rows stable. New workforce-wide labels/mappings require a separate chart-account compatibility plan.
- Keep login `users` separate from `workforce_members`; optional account linking is a later feature and never grants roles automatically.
- Keep advances distinct: earned-payable advances reduce payable; recoverable advances remain receivables until recovered.
- Do not convert attendance, leave, or draft payroll calculations into expenses automatically.

## 4. Test and rollout gates

Do not advance from one release to the next until all applicable checks pass:

1. Every existing driver maps to exactly one workforce member; no missing, duplicate, or cross-tenant mappings.
2. Existing driver/shift APIs and Flutter workflows behave unchanged.
3. Existing ledger balances, recoverable advance balances, and journal totals reconcile before and after the new identity dimension is introduced.
4. A completed shift creates exactly one earning and one wage journal; payroll reports it without re-posting.
5. Concurrent shift finish/payment/advance/recovery and payroll approval cannot duplicate or overdraw worker balances.
6. Shift corrections retain exact old/reversal/replacement journal and ledger lineage; forged direct inserts remain rejected by MySQL.
7. Tenant A cannot reference Tenant B's workforce member through any API, FK, journal, ledger, assignment, or compensation agreement.
8. MySQL integration tests cover backfill constraints, composite FKs, trigger order, idempotency, rollback atomicity, and locking.

Use a staged rollout:
- deploy additive nullable columns/tables
- run preflight and backfill
- verify mapping/reconciliation
- enforce NOT NULL/unique constraints
- deploy compatible backend reads/writes
- deploy mobile only when response contracts are stable
- monitor reconciliation before enabling generic workforce events

Rollback must never delete backfilled workforce identities or posted financial history. Before each enforcement migration, define whether rollback is code-only or requires a forward repair migration.

## 5. Open design questions to resolve before implementation

1. Should workforce identity store national ID as optional personal data from the first release, or should it be deferred until a documented privacy/access policy is ready?
2. Which role codes are required for the first contractor-market release, and which are merely future extensibility?
3. Should one person be allowed to have multiple driver/operator profiles or equipment eligibility profiles? The proposed one-to-one driver link says no for now.
4. Which existing driver rate fields should be migrated into the first CompensationAgreement, and how should three shift-specific rates map to the agreement's basis/components?
5. Should generic non-shift payroll recognition be delivered immediately after the workforce subledger, or should we first ship identity/assignments and leave payroll calculation for the next release?

## 6. Approval boundary

This is a proposed technical design, not an implementation. No migration files, Java/Kotlin/Dart code, API contracts, database objects, PRs, or `main` branch were changed. Implementation starts only after review of this specification and explicit approval of the concrete migration sequence.
