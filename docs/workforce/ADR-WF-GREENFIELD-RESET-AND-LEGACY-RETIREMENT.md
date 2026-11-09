# Workforce Greenfield Reset and Legacy Retirement Plan

- **Status:** Approved product direction; implementation in progress
- **Date:** 2026-11-09
- **Working branch:** `feature/workforce-ledger-unification`
- **Decision owner:** Product owner
- **Supersedes:** The live-tenant backfill and zero-downtime migration approach in `ADR-WF-MIGRATION-ZERO-DOWNTIME.md` for the current pre-production build phase

## 1. Context and decision

The product is still in its build phase. Current development/test tenant data is disposable and is not a production customer dataset that must be preserved. Therefore, the project does not need a general-purpose, tenant-by-tenant legacy data migration platform for the current Workforce unification.

Build the intended domain model directly around **WorkforceMember as the canonical person identity**. Remove obsolete legacy schema only after application code, APIs, mobile clients, tests, constraints, and financial posting paths no longer depend on it.

This is a greenfield product reset, not permission to weaken accounting controls or delete domain capabilities that remain necessary.

## 2. Decisions

1. `workforce_members` is the sole canonical identity for every person working with a contractor.
2. Job titles, operational roles, and assignments remain separate from person identity.
3. Compensation agreements and components are the source of compensation terms; do not preserve duplicate wage-rate ownership on a legacy Driver entity.
4. Shifts reference `workforce_member_id` as their sole person identity. Equipment and site remain independent resources.
5. Workforce ledger entries become the sole canonical workforce financial subledger and the only write/read path for person compensation movements.
6. Financial posting, idempotency, tenant isolation, append-only history, correction/reversal semantics, and balanced journal invariants remain mandatory even with empty development data.
7. There must be one authoritative writer for each financial aggregate. No application dual-write and no temporary split authority between Driver and Workforce ledgers.
8. No generic Migration Platform is required for this build phase. Use ordinary forward Flyway migrations, focused reset/rebuild instructions, and integration tests.
9. Keep the current monolith. Do not create a migration microservice or a separate orchestration dashboard.

## 3. Legacy objects targeted for retirement

The following objects are retirement targets, not objects to drop before their consumers have been removed:

- `drivers` table and Driver CRUD/domain/repository code, after operational needs have moved to Workforce, assignments, compensation agreements, or an explicitly justified resource model.
- `shifts.driver_id` and all Driver-ID request/response/filter/query/unique-index dependencies.
- `driver_ledger_entries` and Driver-keyed financial read/write services, after every earning, payment, advance, recovery, adjustment, and correction path uses the Workforce ledger.
- V56 Driver-to-Workforce mirror trigger and legacy mirror-only constraints/indexes.
- Driver-only ledger provenance such as `legacy_driver_id` and `legacy_source_entry_id`, if no retained audit or import requirement needs them.
- Legacy Driver identity synchronization and compatibility endpoints, after Flutter and other clients use Workforce member IDs.

Do not delete Workforce identity, roles, assignments, compensation agreements, financial item definitions, workforce ledger, accounting journals, audit records, or equipment/site resources merely because they were added during the unification work.

## 4. Required target behavior

### Workforce identity and operations

- Create, edit, activate/deactivate, search, and view all people through Workforce APIs.
- Operational eligibility is determined from Workforce state and active assignments/roles, not the existence of a Driver row.
- Keep operational availability only where it represents a real business concept; do not keep Driver-specific wage-rate fields when compensation agreements own pay terms.
- Shift commands, filters, responses, live status, and mobile screens use Workforce member IDs.
- Enforce tenant-safe foreign keys and one active shift per tenant/member/resource combination using canonical IDs.

### Compensation and financial ledger

- Resolve shift compensation from the effective compensation agreement and approved work measurements.
- Use one Workforce-native ledger writer for shift earnings, payments, earned-pay advances, recoverable advances/recoveries, adjustments, and correction reversals/replacements.
- Journal posting and Workforce ledger insertion must share the same database transaction.
- Preserve the established lock order and idempotency fingerprint semantics; lock the canonical Workforce member rather than a Driver compatibility row.
- Keep earned-pay advances distinct from recoverable advances.
- Posted journals and ledger movements remain immutable; corrections append linked reversals/replacements.

## 5. Implementation sequence

1. **Baseline audit:** enumerate every backend, mobile, platform-web, SQL trigger, index, foreign key, test, and documentation dependency on Driver/driver_id/driver_ledger_entries.
2. **Canonical operations:** move remaining Driver operational behavior to Workforce member/assignment/compensation domain objects. Avoid putting equipment-specific state on the person identity.
3. **Shift identity cutover:** change backend commands and queries, live status, constraints, DTOs, and Flutter payloads to Workforce member IDs. Remove Driver as a required shift dependency.
4. **Financial writer cutover:** implement and test the complete Workforce-native writer as one atomic change across all movement and correction paths. Switch all statements and balances to Workforce ledger. Do not dual-write.
5. **Legacy retirement:** add a forward Flyway contract migration that drops obsolete Driver tables/columns/triggers/indexes only after source-level references and integration tests are clean.
6. **Clean-install verification:** build a fresh MySQL database from Flyway migrations, start backend and mobile flows, create a tenant and Workforce members, run shifts and all financial scenarios, and verify tenant isolation/concurrency.
7. **Documentation:** update API contracts, architecture docs, local rebuild instructions, and tests to describe the final canonical model.

## 6. Migration policy during development

- Do not rewrite already-applied Flyway migrations in place.
- Prefer forward migrations while the current branch evolves. A final schema baseline/squash may be considered only after the model stabilizes and before production onboarding, with a verified clean-install path.
- Since development data is disposable, test databases may be dropped and recreated deliberately. This does not justify dropping schema objects while application code still references them.
- No live-tenant data backfill, tenant migration state machine, shadow-read framework, or generic reconciliation dashboard is in scope.
- Keep historical journal immutability and financial integrity controls; empty data does not make those controls optional.

## 7. Completion gates

Legacy Driver objects may be removed only when all are true:

- No backend production code imports or queries Driver/DriverRepository/DriverLedgerEntryRepository.
- No API contract or supported Flutter screen sends or expects Driver IDs.
- Shifts and live status use Workforce IDs end-to-end.
- All workforce financial commands and statements use the Workforce ledger.
- No trigger or database constraint requires the legacy Driver tables.
- Backend integration tests, Flyway clean-install tests, and Flutter analysis/tests pass.
- The final contract migration succeeds on a fresh MySQL database.

## 8. Explicit boundaries

This decision authorizes implementation and deletion of obsolete **development schema** on the working feature branch after the gates above. It does not authorize:

- Changes to `main`, PR merge, or production deployment.
- Deleting accounting journals, audit history, or core domain tables.
- Weakening financial immutability, idempotency, tenant isolation, or concurrency guarantees.
- Introducing dual-write or two competing financial writers.
- Deleting legacy objects before their application and database dependencies are removed.
