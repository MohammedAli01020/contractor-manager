# PR-WF-01 — WorkforceMember migration specification

**Status:** Merged through PR #34 into `feature/financial-foundation` as squash commit `dc181ebb8de55edf866943cf7b69e173b0dd90b6`; required Backend CI passed on head `cfab4e4c24b5ccc2c3a081509ef1c7505c0b2109`  
**Date:** 2026-11-04  
**Target implementation base:** `feature/financial-foundation` (revalidate branch head immediately before implementation)  
**Reviewed source baseline:** `e03e49b1fbe882e08dde702acb7a9ff3fcd05c84`  
**Related:** `workforce-core-migration-proposal-v1.md`, `workforce-legacy-dependency-inventory-v1.md`, `workforce-core-implementation-roadmap-v1.md`

## 1. Purpose and strict scope

Introduce a tenant-scoped general person identity and link every existing driver/worker operational profile to it without changing existing operational or financial behavior.

PR-WF-01 is deliberately additive:
- Create `workforce_members`.
- Add a nullable `drivers.workforce_member_id`.
- Backfill one workforce member for each existing driver.
- Enforce tenant-safe referential integrity and one-to-one mapping.
- Add read-only/internal domain access and MySQL integration tests only if needed to validate the mapping.

Explicitly out of scope:
- No changes to `shifts.driver_id`, journal lines, `driver_ledger_entries`, financial account codes, posting logic, driver rates, availability triggers, correction rules, or idempotency.
- No public API contract changes and no Flutter changes.
- No role/assignment, compensation, payroll, attendance, leave, or login-account linking.
- No rename/drop of `drivers`, `driver_id`, or `driver_ledger_entries`.
- No merge to `main`.

The current `DriverService` remains the sole application write entry point and `Driver` remains the source of truth for person fields during PR-WF-01. The service creates and synchronizes the linked `WorkforceMember` in the same database transaction, so there is one controlled write path rather than independent writers. Canonical identity write ownership moves to WorkforceMember only in PR-WF-02, with legacy endpoints preserved through that same transactional service.

## 2. Verified source constraints

The reviewed baseline has:
- `Driver` extending `BaseTenantEntity`, with tenant ID, optimistic version, timestamps, name, phone, national ID, notes, active state, worker type, availability, and three shift rates.
- `drivers` with a tenant FK and unique `(id, tenant_id)`, making a composite tenant-safe FK possible.
- `Shift` and multiple financial structures referencing the legacy driver ID.
- The current ledger entity mapped to `driver_ledger_entries`; the table has non-null `driver_id`, tenant-safe driver FK, journal and optional shift FKs, request idempotency uniqueness, statement index, and append-only triggers.
- Driver APIs and mobile screens are still driver-oriented.

These facts mean PR-WF-01 must not touch the ledger or operational foreign keys.

## 3. Proposed schema

### 3.1 New table: `workforce_members`

Proposed columns:
- `id BIGINT NOT NULL AUTO_INCREMENT` primary key.
- `tenant_id BIGINT NOT NULL`.
- `full_name VARCHAR(150) NOT NULL`.
- `phone VARCHAR(30) NULL`.
- `national_id VARCHAR(30) NULL`.
- `notes VARCHAR(500) NULL`.
- `active BOOLEAN NOT NULL DEFAULT TRUE`.
- `version BIGINT NOT NULL DEFAULT 0`.
- `created_at DATETIME(6) NOT NULL`.
- `updated_at DATETIME(6) NOT NULL`.

Required keys:
- Primary key `(id)`.
- Unique key `(id, tenant_id)` for future tenant-safe composite references.
- Index `(tenant_id, full_name, id)` for tenant-scoped directory lookup.
- FK `tenant_id -> tenants(id)`.

Do not add uniqueness to phone or national ID in this migration. The product has not defined whether either is unique per tenant or globally, and real contractors may have incomplete or shared contact data. National ID must not be exposed in broad list responses; access policy belongs in the API/security phase.

### 3.2 Legacy link: `drivers.workforce_member_id`

Add nullable `BIGINT` column first.

Add:
- Index on `(tenant_id, workforce_member_id)`.
- Unique key on `(tenant_id, workforce_member_id)` to ensure a person has at most one driver operational profile during this first model.
- Composite FK `(workforce_member_id, tenant_id) -> workforce_members(id, tenant_id)`.

The column remains nullable during schema deployment and backfill. It becomes NOT NULL only after the preflight and postflight gates pass for every tenant. If production rollout requires a separate enforcement migration, that enforcement is a separately reviewable step, not an assumption hidden inside the initial DDL.

### 3.3 Identity field ownership in this phase

During PR-WF-01:
- Existing `drivers` columns remain the source of truth and all current APIs keep their existing request/response contracts.
- Backfill copies identity values into `workforce_members`.
- `DriverService` creates the linked member on every new driver and synchronizes name, phone, national ID, notes, and active state on updates/deactivation in the same transaction.
- No second API/service is allowed to write workforce identity independently in this phase.

During PR-WF-02:
- A single application service changes the write owner to `workforce_members`.
- Legacy driver APIs retain their existing request/response shape and delegate identity updates to that service.
- Any legacy columns retained temporarily are either read-only compatibility mirrors maintained transactionally by that one service, or removed only in a later additive cleanup after usage is proven absent.
- Reconciliation detects divergence; there must never be two independent writers.

This sequencing removes ambiguity about which copy is authoritative at each release.

## 4. Backfill design

For every existing `drivers` row, create exactly one `workforce_members` row with:
- same `tenant_id`;
- `full_name = drivers.name`;
- `phone = drivers.phone`;
- `national_id = drivers.national_id`;
- `notes = drivers.notes`;
- `active = drivers.active`;
- `version = drivers.version`;
- original `created_at` and `updated_at` values where supported by the migration approach.

Then set `drivers.workforce_member_id` to the created member ID.

Preserve all existing driver primary keys. Do not try to make workforce member IDs equal driver IDs; they are separate identity spaces and must not be coupled.

Backfill must be retry-safe:
- A row already linked to a valid member must not create another member on retry.
- An existing member link must be checked for same-tenant ownership.
- A partially completed migration must be detectable and repairable without duplicate members.
- Use a deterministic migration mapping strategy (for example, a temporary migration mapping table keyed by `(tenant_id, driver_id)`, or a controlled insert/update procedure supported by the chosen Flyway/MySQL migration pattern). Choose the exact mechanism after checking the production MySQL version and migration conventions.
- Avoid assuming auto-increment IDs are contiguous or predictable.

Do not infer or merge duplicate humans based on matching names, phone numbers, or national IDs. PR-WF-01 is a one-to-one copy of each existing driver row. Person deduplication is a separate, audited business workflow.

## 5. Preflight checks

Before applying the migration, run read-only checks and save the output with the release evidence:

1. Confirm MySQL server version and Flyway schema version.
2. Count all drivers globally and by tenant.
3. Find drivers with null tenant IDs or tenants that do not exist.
4. Confirm `drivers(id, tenant_id)` uniqueness and identify any duplicate anomalies.
5. Inspect existing national-ID and phone data quality, but do not reject rows merely because these fields are null or duplicated.
6. Confirm no existing `workforce_member_id` column/table from a partial or manual deployment.
7. Confirm every foreign key/index name to be added is unused.
8. Record per-tenant counts and identity-field checksums or equivalent reconciliation evidence.

If any tenant ownership anomaly or duplicate primary-key anomaly exists, stop before DDL/backfill and resolve it explicitly. Do not silently skip bad rows.

## 6. Postflight and reconciliation

The migration is complete only when all checks pass:

- Every driver has exactly one non-null `workforce_member_id`.
- Every linked member exists and has the same `tenant_id`.
- No workforce member is linked to more than one driver.
- Every newly created member maps to exactly one source driver for this release.
- Name, phone, national ID, notes, active state, and tenant ID match the source driver at migration time.
- Per-tenant driver count equals per-tenant mapped-member count.
- No driver IDs, shift IDs, journal IDs, ledger IDs, or financial values changed.
- Existing driver, shift, ledger, payment, advance, correction, and accounting integration tests remain green.

Retain a migration report with counts by tenant and any exceptions. Do not place raw national IDs in logs, CI output, or ordinary migration reports; compare securely or use non-reversible checksums where practical.

## 7. Application impact

Expected application changes are intentionally small:
- Add `WorkforceMember` JPA entity and tenant-scoped repository.
- Map `Driver.workforceMemberId` without exposing it in the legacy response DTO.
- Create and synchronize the member from the existing `DriverService` transaction; avoid independent write services.
- Do not change DriverController routes, request/response JSON, Flutter models, shift selection, or ledger endpoints.
- Do not expose the new table through a public endpoint in this PR.
- Keep optimistic locking and tenant-scoped repository access consistent with `BaseTenantEntity`.


## 8. Test plan

Use the repository's actual MySQL integration-test setup; H2-only tests are insufficient for composite FKs, MySQL DDL, triggers, and migration behavior.

Required evidence:
- Fresh database Flyway migration succeeds.
- A dedicated MySQL Testcontainers migration test stops at V35, inserts legacy tenant/driver rows, applies V36, and verifies field/timestamp/version preservation and exact mapping.
- Upgrade migration succeeds against a representative database containing existing tenants and drivers.
- Composite FK rejects a link to another tenant's workforce member.
- Unique key rejects two driver profiles linked to the same workforce member.
- FK rejects a nonexistent workforce member.
- Backfill creates one mapping per driver and is safe to rerun/repair.
- Null phone/national ID/notes are preserved correctly.
- Existing driver CRUD/API behavior remains unchanged.
- Existing shift completion/correction and ledger posting tests remain unchanged and pass.
- Transaction failure during mapping does not leave a partially accepted mapping state.
- Postflight reconciliation query returns zero exceptions.

## 9. Rollback and repair policy

Before any application code or later release depends on workforce identities:
- A failed deployment may be rolled back at the code level.
- If the migration is wholly unused and no workforce references or business data have been created, a reviewed rollback may remove only the newly introduced link/FK/index/table.
- Never drop or rewrite any legacy driver, shift, journal, ledger, or payment data.

After workforce identities are referenced by application records or financial dimensions:
- Do not delete workforce members or clear their links as a routine rollback.
- Prefer a forward repair migration or a code rollback that continues to tolerate the additive schema.
- Preserve all identity IDs and historical links.
- A rename/drop is not a rollback strategy.

Production Flyway migrations are treated as forward-only. The exact operational recovery command and backup checkpoint must be written into the deployment runbook before production rollout.

## 10. Ledger naming and compatibility decision

The physical table remains `driver_ledger_entries` during all early workforce releases.

In the later workforce-payable phase:
- Add `workforce_member_id` and backfill it through the existing `driver_id -> drivers.workforce_member_id` mapping.
- Keep `driver_id`, current unique keys, journal/shift references, idempotency, append-only triggers, and historical statements intact during compatibility.
- Generalize application services and queries only after every row reconciles to exactly one workforce member.
- Do not create a second ledger and do not dual-write a single earning/payment/advance into two ledgers.
- Only after all code, reporting, integrations, migrations, triggers, correction paths, and operational tooling stop depending on the old table name may a separate rename be considered.

**Recommended default:** do not rename the physical table unless there is a concrete operational benefit. A stable legacy table name is preferable to a risky cosmetic migration. If a later rename is justified, do it in a dedicated compatibility release with a tested old-name compatibility strategy and a separate approval. The logical domain/API may be called Workforce Payables before the physical table is renamed.

## 11. Acceptance gate and approval

The product owner authorized PR-WF-01 on 2026-11-04. This authorization covered only the scoped additive identity table, driver mapping, and transactional compatibility behavior described here. PR-WF-01 was merged into `feature/financial-foundation` only; it does not approve PR-WF-02, ledger changes, payroll, a table rename, or changes to `main`.

Before implementation, revalidate the financial branch head and repeat the source inventory for all touched migrations, entities, tests, and tenant constraints. If the source has materially changed, update this specification and request approval again.

**Implementation status:** Code and migration were merged via PR #34 into `feature/financial-foundation` (squash commit `dc181ebb8de55edf866943cf7b69e173b0dd90b6`). Required Backend CI passed on the PR head. The migration has not been applied to production, and `main` is unchanged.
