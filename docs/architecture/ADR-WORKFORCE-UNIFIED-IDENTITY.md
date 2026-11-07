# ADR: Unified Workforce Identity and Operating Model

- Status: **Accepted**
- Decision date: 2026-11-07
- Scope: Contractor Manager tenant workforce, mobile operations, compensation, and future workforce finance
- Decision owner: Product owner approval recorded in the project conversation

## Context

Contractors employ or engage people in different capacities: engineers, site supervisors, foremen, workers, technicians, drivers, equipment operators, accountants, administrators, and other personnel. A driver/worker-only identity model does not cover the full operating reality and encourages duplicate records when the same person has more than one responsibility.

The product must remain usable by small contractors on mobile while supporting larger teams, multiple sites, role changes, and auditable financial operations.

## Decision

**Workforce Member is the single canonical person identity for every human working with or engaged by a contractor tenant.**

All people-facing workflows must resolve to this identity. The system must not create separate people merely because someone is also a driver, worker, supervisor, engineer, or operator.

The following concepts are distinct and must not be conflated:

1. **Person identity** — stable tenant-scoped identity and personal/contact data.
2. **Job classification** — profession or trade (for example engineer, supervisor, driver, technician, worker, accountant). A person may have more than one classification where appropriate.
3. **Operational role/capability** — what the person is allowed or qualified to do in a workflow (for example drive, operate equipment, supervise a site). This is not the same as an application login role or authorization.
4. **Assignment** — time-bounded relationship to a project, site, team, equipment, or operational responsibility. A person may have multiple assignments.
5. **Engagement and compensation** — employment/contracting arrangement and effective-dated compensation agreements. Compensation basis is independent of job title.
6. **Application account and permissions** — optional login identity and authorization, separately managed from the workforce person record.

## Product and UX consequences

- Provide a unified Workforce directory and add/edit person flow for all workforce categories.
- The person form captures common identity/contact fields, then category-specific details only where needed.
- Drivers & Workers becomes a specialized operational view/workflow over the same canonical Workforce Members; it is not a second identity store.
- Existing driver and worker experiences may remain as focused shortcuts for day-to-day dispatch/shift operations, provided they reference the same person identity.
- Workforce profiles expose assignments, qualifications/capabilities, compensation agreements, and relevant history according to permissions.
- Mobile-first workflows are required; do not make routine field operations depend on the platform-owner web application.

### Single Workforce navigation (product-owner decision, 2026-11-07)

**Workforce is the only top-level navigation area for managing people.** Do not add separate top-level modules for Drivers, Workers, Engineers, Supervisors, or Technicians.

Within Workforce, provide:
- One searchable directory and one canonical add/edit person flow.
- Filtered classifications/views (All, Workers, Drivers/Operators, Engineers, Supervisors, Technicians, Administrative staff, and other tenant-relevant categories).
- A single person profile with common identity/contact information and progressive, classification-specific operational sections.
- Separate sections/tabs for assignments, qualifications/capabilities, compensation agreements, attendance/work history, and account access, shown according to authorization.

A filtered view is not a separate identity store. A person with multiple classifications appears as one person in the directory and may appear in more than one filtered view without duplicate records.

Legacy driver/worker operational screens may temporarily remain as compatibility routes or focused shortcuts during migration, but they must be reached from Workforce or route their add/edit actions into the canonical Workforce flow. They are not separate top-level people-management destinations in the target navigation. Preserve dispatch/shift-specific workflows where they add operational value; consolidate identity management, not every operational task into one generic screen.

The common mobile flow should be short: create the person once, select one or more classifications/capabilities, and reveal only the relevant additional fields. Driver licence/dispatch details, engineering/site details, supervisor/team details, and technician/equipment details belong to progressive profile sections rather than separate person-creation systems.

## Compensation policy

Compensation is independent from job classification. Examples include monthly salary, day rate, shift rate, hourly rate, quantity/piece rate, and fixed contractual amount.

Only expose a compensation basis as operationally supported after its complete lifecycle is implemented and tested: agreement creation, approval/effective dates, resolution into operations, immutable historical snapshots, corrections/replacements, and financial posting/reporting as applicable.

In particular, do **not** present DAY or FIXED as fully supported merely because enum/backend representation exists. Keep support claims limited to verified end-to-end behavior. Existing supported SHIFT, HOUR, and QUANTITY paths remain subject to integration and real-device validation.

## Data integrity and migration constraints

- Preserve existing driver/worker records and identifiers during transition.
- Link legacy operational records to canonical Workforce Members; do not bulk-delete, recreate, or silently merge people.
- Detect and resolve duplicate candidates with explicit, auditable rules; ambiguous matches require review rather than automatic merging.
- Retain legacy `driver_id` references and API compatibility during a staged migration where needed; use `workforce_member_id` as the canonical identity for new cross-workforce features.
- Preserve historical shift, compensation, and ledger snapshots. A person's current profile or agreement must not rewrite historical financial facts.
- Enforce tenant isolation for every directory, assignment, agreement, and operational query.
- Protect national ID and other sensitive personal data with least-privilege access; do not expose it in directory/search responses by default.
- Use additive, reversible migrations where practical, with backup and rollback planning before production rollout.

## Delivery sequence

1. Review the existing Workforce identity API and mobile compensation UI against this ADR.
2. Implement unified create/edit and directory workflows for all workforce categories.
3. Safely link legacy drivers/workers to canonical identities, with duplicate review and regression coverage.
4. Implement job classifications, operational capabilities, and effective-dated site/project assignments.
5. Complete and validate compensation lifecycles for each basis before advertising it as supported.
6. Build workforce ledger, advances, pay runs/settlements, and related reporting on canonical identities.
7. Consider worker self-service and offline-first synchronization in the later planned phase; current design should avoid blocking them.

## Acceptance criteria

- A person can be created once regardless of whether they are an engineer, supervisor, worker, driver, operator, technician, or administrator.
- The same person can hold multiple classifications/capabilities and multiple valid assignments without duplicate identity records.
- Driver/worker operational workflows resolve to the same Workforce Member.
- Compensation basis is selected independently of job title and is only advertised when end-to-end verified.
- Historical operational and financial records remain stable after profile, assignment, or agreement changes.
- Tenant isolation and personal-data access controls are covered by automated tests.
- Mobile users can complete the common workforce workflows without relying on desktop-only screens.
- A Workforce Member can exist and participate in operations without an application account; access can be linked later without creating a duplicate person.
- Suspending or revoking an account does not delete workforce or historical financial/operational records.
- Application permissions are independent from job classifications and are enforced by backend authorization with tenant and assignment scope.
- Sensitive personal and financial information is not exposed to ordinary workforce accounts by default.
- The common add-person flow remains short and mobile-first, with optional/progressive category-specific fields.

## Explicit non-goals

- Replacing every operational driver/worker screen with one generic screen in a single release.
- Treating every workforce member as an application user.
- Assuming every person is an employee; contractors and other engagement types must be representable.
- Enabling DAY/FIXED or any other compensation basis solely because the database/API accepts its code.
- Introducing microservices for this domain now; retain the current modular monolith unless evidence later justifies a change.

## Operational addendum: driver creation and compensation scope

### Driver creation entry point

- The canonical create-person entry point is **Workforce → Add member**.
- A driver is created there once, as a Workforce Member. The user selects the relevant job classification and operational capabilities (for example driver and/or equipment operator).
- When the driver capability/classification is selected, the form progressively reveals driver-specific operational fields (for example licence number, licence class, expiry date, and any fields required by dispatch/vehicle workflows).
- Saving the profile creates or links the required driver operational profile to that same Workforce Member; it must not create a second person identity.
- Existing **Drivers & Workers** screens may remain as filtered operational shortcuts for dispatch and shift workflows. Their add actions must route into the canonical Workforce create flow (with driver fields preselected), or create/link the operational profile against an existing Workforce Member. They must never create an independent duplicate person.
- Existing driver records must be safely linked/migrated with their identifiers and historical shift references preserved. Ambiguous identity matches require review.

### Compensation scope and multiple work engagements

Compensation must be resolved against a **work engagement / assignment scope**, not treated as a permanent property of the person and not freely reselected as an arbitrary pay basis on every shift.

- A compensation agreement is effective-dated and scoped to a defined engagement/assignment context (such as a site, project, equipment responsibility, work type, or other explicitly modelled scope).
- A member may have more than one concurrent engagement where the scopes are distinct and non-conflicting. Each engagement may have its own compensation agreement and basis.
- For a normal shift, the app resolves the applicable approved agreement from the member, assignment, work context, and work date. The user should not manually choose SHIFT/HOUR/QUANTITY when there is exactly one unambiguous applicable agreement.
- If multiple applicable agreements remain possible, or no valid agreement exists, the app must require an explicit, auditable resolution and must not silently guess or post a payable amount.
- Separate work performed on the same day under different engagements must be recorded as separate work records, each tied to its applicable agreement. Example: a base SHIFT engagement and a separately authorized HOUR engagement for additional work.
- Changing the primary compensation agreement is different from authorizing a separate additional engagement. A replacement agreement must not be used to represent unrelated extra work.
- If the compensation basis changes during one continuous work activity, split the work record at the approved effective boundary or use an explicitly modelled mixed-compensation record; never combine incompatible bases implicitly.
- Every completed work record preserves an immutable snapshot of the agreement/scope, basis, rate, unit, measured quantity or hours, and calculation result used at completion. Later agreement changes do not recalculate historical work.
- Overlap validation must be based on effective dates **and scope**. Agreements for distinct scopes may coexist; conflicting agreements for the same scope and period must be rejected or explicitly resolved.

### Delivery implications

1. Keep the current Workforce directory and compensation screens as foundations; do not imply the complete multi-engagement model is already implemented.
2. Deliver the canonical driver/member add flow and legacy operational-profile linking before removing any legacy shortcuts.
3. Define assignment/engagement scope and agreement resolution rules before implementing concurrent compensation agreements.
4. Add backend integration tests for no agreement, one applicable agreement, conflicting agreements, distinct concurrent scopes, same-day separate SHIFT and HOUR work, effective-date boundaries, and immutable historical snapshots.
5. Add mobile acceptance tests proving that driver creation produces one person identity and that shift entry resolves the correct agreement without arbitrary pay-basis selection.

## Approval

The product owner explicitly approved this unified Workforce direction on 2026-11-07. This ADR records the approved target architecture; it does not claim that all listed workflows have already been implemented or validated.
