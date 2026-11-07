# ADR — Canonical Workforce and People Model

- **Status:** Accepted
- **Decision date:** 2026-11-07
- **Applies to:** Contractor Manager tenant domain, mobile workflows, operations, and workforce-related financial flows
- **Decision owner:** Product approval recorded in the project conversation

## Context

The product must support contractors whose people include engineers, site supervisors, foremen, skilled and general workers, drivers, equipment operators, technicians, administrators, and other personnel. A model centered only on drivers and workers does not represent the full workforce and risks creating duplicate people records as new workflows are added.

The product is mobile-first and must remain practical for small contractors while preserving tenant isolation, reliable operational history, and financial correctness.

## Decision

**Workforce Member is the single canonical identity for every person working with or for a contractor.**

Every person is represented by one tenant-scoped workforce member record, regardless of job title, operational responsibilities, or compensation method. Screens and modules may be specialized for a workflow, but they must reference the canonical workforce identity rather than create competing person records.

### Separate identity, classification, assignment, and compensation

These concepts must remain distinct:

1. **Person identity:** canonical workforce member and personal/contact details.
2. **Worker classification:** job title, profession, skill, and worker type (for example engineer, supervisor, driver, operator, technician, or administrator).
3. **Operational role:** what the person is authorized or expected to do in a particular workflow (for example assigned driver, site supervisor, or equipment operator).
4. **Assignment:** where and when the person is assigned, including project/site scope and any relevant operational links.
5. **Compensation:** independently effective-dated agreements and compensation components. A person's job title must not dictate their pay basis.

A person may have multiple roles or assignments over time, and may be assigned to more than one site where business rules permit. This must not create another person identity.

### Canonical profile

The unified workforce profile is the home for:
- Personal and contact details, including national ID where permitted by role and privacy policy.
- Job title, profession, skills, and workforce classification.
- Active/inactive status and lifecycle history.
- Operational roles and site/project assignments.
- Compensation agreement history and effective dates.
- Later workforce financial views such as earned amounts, advances, deductions, and settlements, subject to their own authorization and accounting rules.

Sensitive personal data must not be exposed in broad directory/list responses. Access to national ID and other sensitive fields remains permission-controlled and must not be weakened by this decision.

### Existing Drivers & Workers experience

The existing Drivers & Workers screen may remain as a specialized operational view for driver/worker workflows. It is **not** a separate source of person identity.

Existing driver records and their operational identifiers must be linked to the canonical workforce member identity. Preserve legacy identifiers such as `driverId` where existing APIs and shift history require them; do not confuse them with `workforceMemberId`.

The migration/linking strategy must be backward-compatible, tenant-safe, auditable, and non-destructive. Do not duplicate people or silently discard legacy data. Where identity matching is ambiguous, require an explicit resolution rather than guessing.

### Compensation principles

Compensation basis is independent from job classification. Examples include monthly salary, day rate, hourly rate, shift rate, quantity/piece rate, or fixed amount, as supported by a complete business lifecycle.

Do not expose a compensation basis as production-ready merely because an enum or partial backend path exists. In particular, **DAY and FIXED must remain unavailable in user-facing workflows until their complete agreement, operational capture, calculation, approval, correction, and financial posting lifecycle is implemented and tested**.

Compensation changes must preserve historical snapshots and posted financial records. No retroactive recalculation or mutation of posted journal entries is implied by this decision.

## Product structure

The target product areas are:

- **Workforce directory and profile management:** add, view, edit, search, and manage all workforce members.
- **Roles, skills, and assignments:** classify people and assign them to sites/projects and operational responsibilities.
- **Operational views:** specialized driver, worker, operator, or supervisor experiences that use the canonical identity.
- **Compensation agreements:** effective-dated rates and terms independent of job title.
- **Workforce financial lifecycle:** earned pay, advances, deductions, settlement/pay runs, and reconciliation, delivered in separate validated phases.

The unified add-member flow must support the full set of workforce types; adding engineers and supervisors must not require misclassifying them as drivers or general workers.

## Delivery sequence

1. Define and validate the canonical workforce data model and its relationships to legacy driver/worker records.
2. Deliver a unified add/edit member flow with configurable workforce classifications.
3. Link existing records safely and preserve legacy operational references and history.
4. Deliver role, skill, site, project, and operational assignment management.
5. Complete compensation agreements and operational capture for each supported compensation basis, one lifecycle at a time.
6. Deliver workforce ledger, advances, settlement/pay runs, and reconciliation with appropriate accounting controls.
7. Consider offline-first synchronization in its separately planned phase; current design should avoid blocking that direction.

## Explicit constraints

- Tenant isolation applies to all workforce identities, profiles, assignments, agreements, and financial data.
- Preserve auditability, idempotency, concurrency safety, and authorization boundaries.
- Do not alter historical shifts, compensation snapshots, or posted journals as a side effect of profile or agreement changes.
- Do not delete or rewrite production data as part of schema/model alignment.
- No production or Railway environment access is authorized by this ADR.
- No PR merge or change to `main` is authorized by this ADR.

## Consequences

- New person-centric features must use Workforce Member as their identity anchor.
- Existing driver/worker functionality will be integrated progressively, not abruptly removed.
- The current WF10B mobile agreement screen is an incremental slice, not proof that the complete workforce lifecycle is finished.
- The roadmap must prioritize unified member creation and safe legacy linking before expanding role-specific workflows.
