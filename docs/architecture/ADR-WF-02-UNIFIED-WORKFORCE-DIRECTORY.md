# ADR-WF-02 — Unified Workforce Directory and Legacy Module Consolidation

- **Status:** Accepted product direction; implementation in progress
- **Date:** 2026-11-07
- **Scope:** Flutter mobile navigation, Workforce directory, backend identity and operational APIs, and future platform-owner/tenant web surfaces

## Context

The product currently has two competing mobile entry points: a legacy "السائقون والعمال" screen backed by `/drivers`, and a Workforce directory backed by `/workforce/members`. The Workforce directory currently reads canonical identities but its empty state directs users back to the legacy screen for creation. The legacy Driver API still creates both a Driver row and a WorkforceMember, and requires driver-specific legacy shift rates. The current directory projection joins WorkforceMember to Driver and exposes the legacy worker type, so it is not yet a complete all-person directory.

This split is inconsistent with the approved product model: one person identity, one Workforce management area, multiple capabilities and assignments, and specialized operational workflows only where needed.

## Decision

1. **One top-level people-management area:** Workforce is the only top-level area for managing people.
2. **One canonical identity:** `workforce_members` is the source of truth for person identity. A person must not be duplicated because they have multiple professions or assignments.
3. **One searchable directory:** Workforce provides search and filters for category/capability, active state, site/project, and other useful operational dimensions as the backend supports them.
4. **One create/edit entry point:** new people are created and common identity fields are edited through Workforce. Category-specific fields are progressively shown in the same person flow.
5. **Multiple capabilities and assignments:** job capabilities/classifications are distinct from effective-dated site/project/team assignments. Existing `workforce_roles` and `workforce_assignments` must be audited before reusing them as a permanent capability catalogue.
6. **Operational workflows remain specialized when justified:** shift dispatch, equipment assignment, availability, and other operational tasks may retain focused screens. They must reference the same Workforce identity and must not create a second person.
7. **Legacy screens become filtered views or compatibility routes:** Drivers and Workers are not separate top-level people-management modules. During migration, old routes may remain for compatibility, but their add/edit actions must route to Workforce and preserve all operational behavior.
8. **No speculative profile tables:** do not create `driver_profiles`, `worker_profiles`, `engineer_profiles`, or similar tables solely because a person has a particular job title. Add a specialized extension only when it stores genuinely unique operational attributes that cannot be modeled cleanly as a shared capability, assignment, qualification, or document.
9. **Financial records stay separate:** workforce subledger movements and accounting journal entries are different concerns. Do not rename or delete `driver_ledger_entries` until all references, semantics, constraints, and historical reports have been inspected.

## Current code audit (branch `feature/wf11a-workforce-member-creation`)

### Flutter mobile

| Current component | Observed behavior | Target action |
|---|---|---|
| `HomeScreen` card "السائقون والعمال" | Opens `DriversScreen` | Remove as a top-level menu entry after Workforce can cover required tasks |
| `HomeScreen` card "القوى العاملة والأجور" | Opens `WorkforceScreen` | Keep and rename to "القوى العاملة" |
| `DriversScreen` | Uses `GET /drivers`, `GET /shifts/running`; owns legacy driver add/edit/deactivate and availability display | Retain operational capabilities during migration; remove separate people-management entry point; route add/edit to Workforce once operational profile editing is supported there |
| `WorkforceScreen` | Uses `GET /workforce/members`; currently search + include-inactive; opens compensation agreements; no canonical add/edit UI yet | Make it the complete people directory with filters and canonical create/edit |
| `DriverEditDialog` | Posts/updates `/drivers` and requires driver-specific fields/rates | Do not use as generic person creation; progressively move driver-only operational fields into the Workforce person flow |

The duplicate top-level mobile card is removed in this slice. The legacy Drivers screen and API are **not deleted**.

### Backend

| Component | Observed behavior | Target action |
|---|---|---|
| `WorkforceController` | Directory, canonical create/detail/update, roles, assignments | Keep and extend with safe category/capability filters and directory metadata |
| `WorkforceMemberRepository.searchDirectory` | Left-joins Driver and filters by identity search/active state; directory role is derived from legacy Driver.workerType | Replace the driver-only projection gradually with a canonical directory projection that supports people without Driver rows and multiple capabilities without duplicate rows |
| `DriverController` / `DriverService` | Operational API still creates Driver + WorkforceMember and synchronizes identity from Driver on updates | Preserve for compatibility initially; make Workforce the canonical identity writer and stop legacy writes from overwriting canonical identity |
| `workforce_roles` / `workforce_assignments` | Tenant-scoped roles and effective-dated site assignments | Keep; inspect role seed semantics before treating role codes as profession/capability taxonomy |
| `drivers` and `driver_id` shift references | Existing operational and historical dependency | Keep until a reviewed, tested migration proves safe |
| `driver_ledger_entries` | Semantics/dependencies not yet fully audited | No rename/drop decision or migration in this slice |

### Removal / deprecation inventory

**Removed now**
- The duplicate "السائقون والعمال" top-level card from the mobile home screen.

**Retained for compatibility and operational use**
- `DriversScreen`, `DriverEditDialog`, `/api/v1/drivers`, `drivers`, `driver_id` references, driver availability and dispatch behavior.
- Compensation agreements, workforce assignments, and all existing financial journal behavior.

**To deprecate only after replacement and acceptance**
- Legacy Driver/Worker create/edit as an independent identity flow.
- Driver-only directory as the primary people directory.
- Any duplicate person-management routes in mobile or web navigation.

**Not approved for deletion**
- Any database table, column, endpoint, entity, or financial record. No destructive migration is authorized by this ADR.

## Delivery sequence

1. Consolidate navigation so Workforce is the only top-level people-management entry.
2. Complete canonical create/edit in Flutter, with common identity fields and category-specific progressive operational fields.
3. Add role/capability-aware directory filters and person detail sections. Avoid duplicate directory rows for people with multiple roles.
4. Convert legacy Drivers/Workers add/edit actions into Workforce entry points while retaining specialized operational actions and shift compatibility.
5. Audit all backend/web/mobile references, schema foreign keys, queries, reports, tests, and ledger semantics.
6. Additive data migration and dual-read/compatibility period, followed by explicit acceptance.
7. Remove legacy UI/API/schema elements only in separate reviewed steps after proving no remaining consumers and preserving history.

## Acceptance criteria

- A person with multiple capabilities appears exactly once in Workforce.
- A person without a Driver record appears in the directory and can be created/edited.
- Driver operational availability, active-shift protections, and shift history continue working.
- Filters return the correct identities without leaking cross-tenant data or duplicating people.
- National ID remains excluded from directory/search and protected by role-based access in detail.
- No identity action changes compensation agreements, workforce ledger, or accounting journals.
- Flutter 3.3.3 tests/analyzer, backend integration tests, CI, isolated WF06 validation, and real Android acceptance pass before this phase is considered complete.
- No merge, `main` change, destructive schema change, or production data migration occurs without explicit approval.
