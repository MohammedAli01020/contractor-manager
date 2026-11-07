# WF-11A — Canonical Workforce Member Creation

- **Status:** In progress — canonical identity API implemented in branch; integration tests authored, execution pending
- **Branch:** `feature/wf11a-workforce-member-creation`
- **Base:** `feature/wf10c-compensation-basis-mobile`
- **Tracking issue:** [#52](https://github.com/MohammedAli01020/contractor-manager/issues/52)
- **Product decision:** Workforce is the sole top-level people-management area and the only canonical identity create/edit entry point. Drivers, workers, engineers, supervisors, technicians, and other categories are filtered views/progressive profile sections, not separate people modules or identity stores.

## Verified baseline

The current `WorkforceMember` entity contains only full name, phone, national ID, notes, and active state. The current directory endpoint is read-only.

The current `POST /api/v1/drivers` flow creates a `Driver` and a `WorkforceMember` in one transaction. `DriverRequest` requires morning, evening, and night legacy rates, so it cannot be reused as a generic person-creation request.

The current driver update/deactivate flow calls `WorkforceIdentityService.synchronizeFromDriver`, which copies the driver's name, phone, national ID, notes, and active flag into the Workforce identity. That direction of synchronization must be changed before canonical Workforce editing is enabled; otherwise an old driver update could silently overwrite a newer canonical identity.

The existing `workforce_roles` and `workforce_assignments` model represents effective-dated operational assignments (optionally site-scoped). It must not be casually overloaded as a permanent job-title or capability catalogue.

## Implementation rules

1. Keep the tenant-scoped `WorkforceMember` as the canonical identity. A person can exist without a Driver row, an assignment, a compensation agreement, or an application login.
2. Add ADMIN-only create/update and tenant-scoped detail operations under `/api/v1/workforce/members`. Directory/search remains safe and excludes national ID; detail responses reveal national ID only to ADMIN.
3. Treat job classification and operational capabilities as separate, multi-valued concepts. Audit existing role/assignment semantics and migrations before choosing persistence; do not conflate them.
4. A driver capability may create/link one optional Driver operational profile in the same transaction. Existing `drivers.id` values and shift foreign references remain unchanged.
5. Driver operational fields (availability, dispatch/vehicle-related data, and legacy compatibility wage fields) remain in the operational profile. Identity edits must not modify rates or compensation agreements.
6. Reverse the legacy identity synchronization safely: canonical identity changes are propagated to the linked operational profile where required; legacy operational updates must not overwrite canonical identity fields. Keep lock order consistent and cover both paths with concurrency tests.
7. National ID is never returned in directory/search and is revealed in detail/create/update responses only to ADMIN. Identity PUT is a full replacement of the common editable identity fields; clients must send the latest values and `version`, and may explicitly clear optional fields with null.
8. Existing records are not deleted or automatically merged. Any ambiguous identity/profile match requires explicit review.
9. Keep account/login provisioning separate from person creation. No automatic account creation.
10. No production migration, backfill, or DB-volume reset is part of this phase.


## Implementation progress (2026-11-07)

### Canonical identity API — code added, tests not yet executed

- Added `POST /api/v1/workforce/members` for ADMIN-only creation of a general person identity without requiring a Driver row, legacy wage rates, compensation agreement, or application account.
- Added tenant-scoped `GET /api/v1/workforce/members/{memberId}`. National ID is revealed only to ADMIN; other permitted readers receive the profile without that sensitive value. Audit entries never store the national ID value itself.
- Added ADMIN-only `PUT /api/v1/workforce/members/{memberId}` with mandatory optimistic `version` checking.
- Added canonical identity audit events (`WORKFORCE_MEMBER`) for create and changed identity fields. National ID values are redacted from audit old/new values; the audit records the change using a `[REDACTED]` marker and never stores the submitted identifier.
- Canonical edits lock the linked legacy Driver row before the WorkforceMember row, then update both in one transaction. This matches the existing Driver → WorkforceMember lock order and prevents a stale operational projection from diverging after a canonical edit.
- Added a repository lock query for the linked Driver and a `WorkforceMemberCrudIntegrationTest` covering standalone person creation, linked-driver synchronization, tenant isolation, and directory national-ID privacy.
- Updated the entity documentation to state that WorkforceMember is canonical and operational profiles are optional linked records.

**Not yet implemented:** multi-valued job classifications, canonical attach/create of Driver operational profiles, category-specific progressive profile fields, mobile add/edit UI, and migration of legacy add actions. No tests have been run or claimed passing as part of this documentation update.

## Required test sequence

- Create an engineer/supervisor with no Driver row.
- Create a driver through Workforce and verify exactly one identity plus one linked operational profile.
- Add/remove a capability without creating another identity.
- Update canonical identity and verify the linked driver view reflects it.
- Update operational availability and verify it does not overwrite canonical identity fields.
- Verify national ID privacy and update preservation.
- Verify tenant isolation, ADMIN-only writes, optimistic/pessimistic concurrency, and audit records.
- Verify old driver listing, availability, shift creation/completion, and historical `driver_id` references.
- Verify no compensation agreement or journal/ledger entry is created or modified by identity operations.
- Run Backend CI and Flutter 3.3.3 analyzer/tests; complete real Android acceptance before considering the phase done.

## Delivery order

A. Confirm existing Flyway schema, role seeds, entity relationships, and lock order.
B. Add additive persistence and ADMIN-only identity API with integration tests.
C. Add optional driver operational-profile attach/create behavior without changing existing IDs.
D. Add Flutter Workforce add/edit flow with progressive driver-specific fields.
E. Convert Drivers & Workers add actions into Workforce routes while preserving operational list/detail behavior.
F. Validate on the WF06 isolated test compose project and a real Android device.

## Guardrails

- No PR merges or `main` changes without explicit product-owner approval.
- Keep the modular monolith and existing tenant isolation.
- Do not expose DAY/FIXED compensation.
- Do not couple identity creation to wage/rate entry.
- Do not claim completion until the end-to-end acceptance sequence passes.

## Approved product navigation and UX decision (2026-11-07)

The product owner approved consolidating people management under one top-level **Workforce** area. Do not create separate top-level Drivers, Workers, Engineers, Supervisors, or Technicians modules in the target navigation.

- Workforce owns the single searchable directory and canonical add/edit flow.
- Provide category filters/views inside Workforce (All, Workers, Drivers/Operators, Engineers, Supervisors, Technicians, Administrative staff, and future categories).
- One person may have multiple classifications/capabilities and appears once in the master directory; filtered views must resolve to that same identity.
- Keep one person profile with common identity/contact data and progressive sections for category-specific operational data.
- Keep assignments, qualifications/capabilities, compensation agreements, work history, and optional account access as distinct profile sections with permission-aware visibility.
- Driver licence/dispatch data, engineering/site data, supervisor/team data, and technician/equipment data are specialized profile sections, not separate identity creation flows.
- Existing driver/worker pages may temporarily remain as compatibility routes or operational shortcuts during migration. They are not target top-level people-management modules; their create/edit actions must route into Workforce and must never create a duplicate identity.
- Consolidation applies to people/identity management, not necessarily every operational task. Dispatch, shift, and site workflows may remain specialized where useful, but they consume the same Workforce identity.
- Mobile-first progressive disclosure: capture common fields first, then show only the selected classification's relevant fields.

This is an approved target UX/navigation decision. It does not mean the current mobile navigation or all category-specific profile sections have already been implemented.


## Consolidation audit and implementation tracking

The detailed inventory, retained/deprecated decisions, and acceptance criteria are recorded in [ADR-WF-02 — Unified Workforce Directory and Legacy Module Consolidation](../architecture/ADR-WF-02-UNIFIED-WORKFORCE-DIRECTORY.md).

- [x] Confirm Workforce is the only target top-level people-management area.
- [x] Identify the duplicate mobile home entry and remove it in the current implementation slice.
- [x] Inventory the legacy Drivers screen/API and explicitly retain operational behavior during migration.
- [ ] Complete canonical create/edit UI in Flutter.
- [ ] Add multi-capability directory filters without duplicate people.
- [ ] Route legacy add/edit actions into Workforce.
- [ ] Complete backend schema/FK/query/report and ledger dependency audit before proposing any deletion.
- [ ] Run backend and Flutter validation and real-device acceptance.

No database tables, columns, APIs, or financial records are deleted by this consolidation slice.
