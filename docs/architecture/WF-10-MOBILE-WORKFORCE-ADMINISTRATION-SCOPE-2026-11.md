# WF-10 — Mobile Workforce Administration Scope

**Date:** 2026-11-07  
**Status:** Proposed scope documented for review; implementation not started  
**Base:** `integration/stabilization-2026-11`  
**Tracking:** [Issue #45](https://github.com/MohammedAli01020/contractor-manager/issues/45)

## 1. Objective

Deliver a mobile-first Workforce administration experience for contractor tenants. The feature must use the canonical Workforce identity, preserve compatibility with the existing Driver operational model, and provide safe administration of roles, effective-dated assignments, and compensation agreements.

This document defines scope and sequencing. It does not authorize merging, production changes, or database migrations.

## 2. Verified current-state findings

### 2.1 Two identities must not be confused

The system has a canonical `WorkforceMember` identity and a legacy/operational `Driver` record. The driver record stores a `workforceMemberId` link, but the existing `DriverResponse` does not expose that canonical ID. Driver and workforce-member IDs must never be assumed equal.

Workforce assignment and compensation operations are keyed by `workforceMemberId`. A mobile client that starts from the current driver list therefore lacks the canonical identifier required for those routes.

**Blocking requirement:** before building Workforce mobile workflows, expose a tenant-scoped, explicit mapping between canonical Workforce member ID and linked operational Driver ID. Prefer a dedicated Workforce list/profile API returning both IDs and the operational profile summary. A backward-compatible extension to Driver list/detail is an alternative only if its long-term ownership is explicitly accepted.

### 2.2 Existing mobile experience is legacy Drivers

The current mobile UI manages Drivers through the legacy flow and shows morning/evening/night rates on the operational profile. It is not a complete Workforce administration experience. It does not provide dedicated member profiles, role/assignment management, or compensation-agreement administration.

### 2.3 Backend capabilities already present

The current integration baseline contains:
- Canonical Workforce members synchronized with legacy Driver identity fields.
- Workforce roles and effective-dated assignments.
- Compensation agreement lifecycle and component records.
- Agreement approval/replacement/cancellation operations.
- Shift compensation snapshots, with operational resolver support verified for SHIFT, HOUR, and QUANTITY.

The resolver does not currently operationalize DAY or FIXED for shift wage calculation. Their presence in enums or request validation must not be represented in the UI as end-to-end support.

### 2.4 Permission boundaries

The existing API distinguishes read access from administrative writes. The UI should reflect permissions, but backend authorization remains the security boundary. Assignment and compensation write/approval actions must remain ADMIN-only unless a separately reviewed policy changes that rule.

## 3. WF-10 scope

### WF-10A — API identity and list contract

- Add a tenant-scoped Workforce member list/detail contract.
- Return canonical `workforceMemberId` and, when linked, the operational `driverId` as separately named fields.
- Return the minimum profile fields needed by mobile: full name, contact summary, active state, worker/operational type, and linkage state.
- Support search, pagination, and explicit inclusion of inactive members.
- Preserve existing Driver API behavior; do not silently rename or repurpose IDs.
- Apply tenant isolation and role authorization; add API contract, negative authorization, and cross-tenant tests.
- Avoid returning national ID in list responses. Detail disclosure must follow existing authorization/privacy policy.

**Acceptance:** a client can navigate from a member list to assignment/agreement routes using the canonical member ID, with no ID inference.

### WF-10B — Mobile Workforce list and profile

- Dedicated Workforce navigation entry and list.
- Search and active/inactive filtering.
- Profile summary with clear separation between canonical identity and linked operational Driver profile.
- Arabic RTL, phone-first layout, loading/empty/error/retry states.
- Permission-aware actions; no assumption that hiding a control enforces authorization.

### WF-10C — Roles and effective-dated assignments

- View assignment history, role, site, effective-from/to dates, status, and notes.
- ADMIN can create assignments and end them through the supported lifecycle.
- Preserve history; no hard-delete UX.
- Resolve role/site display labels from authoritative APIs; do not duplicate master data in the client.
- Surface overlapping or ambiguous assignments as explicit server conflicts.

### WF-10D — Compensation agreement administration

- View agreement history, status, effective dates, scope, components, and approval metadata.
- ADMIN can create drafts, approve, cancel eligible drafts, and approve replacements with a reason, following existing API lifecycle rules.
- Display legacy morning/evening/night rates as legacy/fallback values, not as approved agreement components.
- Initially expose only operational SHIFT, HOUR, and QUANTITY choices.
- Do not offer DAY or FIXED until their business semantics and end-to-end execution are designed and implemented.
- Never silently migrate old rates into a new approved agreement.
- Never recalculate historical shift wages when agreements change.

### WF-10E — Hardening and real-device acceptance

- Verify ADMIN and read-only roles on a real Android device.
- Verify small-screen scrolling, RTL, keyboard behavior, loading, empty, error, retry, and network interruption.
- Confirm all write/approval actions require server confirmation.
- Verify no stale or guessed identifiers are used.
- Verify assignment/agreement history and frozen shift compensation snapshots remain unchanged.

## 4. Non-functional and safety requirements

- Tenant isolation is enforced server-side for every list/detail/write route.
- Use existing optimistic-concurrency/version contracts where applicable.
- Preserve audit history and effective dates.
- No hard deletion of members, assignments, or agreements where lifecycle actions exist.
- Do not add client-side wage calculation as the source of truth.
- Do not permit offline financial posting or approval in WF-10. Offline draft caching may be evaluated separately, but server confirmation is required for financial and approval actions.
- Keep the existing monolith and API conventions; do not introduce microservices for this phase.
- Keep the mobile workflow Arabic RTL and usable by field teams primarily on phones.

## 5. Out of scope

The following remain later roadmap work and are not part of WF-10:
- Worker self-service or worker login.
- Workforce ledger migration, advances, recoveries, and pay runs/settlements.
- Monthly fixed salary or payroll execution.
- DAY/FIXED compensation execution.
- Offline financial posting/approval.
- Legal, tax, payroll, or labor-compliance certification.

## 6. Delivery sequence

| Phase | Deliverable | Exit condition |
|---|---|---|
| WF-10A | Canonical identity/list API contract | API tests prove explicit ID mapping, tenant isolation, and authorization |
| WF-10B | Mobile list/profile | Member navigation consistently uses canonical ID |
| WF-10C | Assignment management | History and effective-date lifecycle verified |
| WF-10D | Agreement management | Draft/approval/replacement/history verified |
| WF-10E | Device acceptance and hardening | Real Android acceptance checklist passed |

Each phase should be independently reviewable. No phase is merged until its dependencies and tests pass and the user explicitly approves the merge.

## 7. Dependencies and unresolved policy questions

Before implementing related later phases, decide:
- Which workforce categories require roles and assignments.
- Whether multiple simultaneous assignments are allowed and how a shift identifies its intended assignment.
- Whether and how assignment-specific compensation takes precedence over general agreements.
- DAY meaning: calendar day, attendance/worked day, or shift-day; including partial-day and multiple-shift behavior.
- FIXED meaning and period boundaries, proration, absence handling, and settlement cadence.
- Approvers, cutoff rules, and payment-account policy for future settlements.
- Whether worker self-service is required and what data/actions it may access.

These policy questions must not be guessed by the mobile client.

## 8. Rollout and merge guardrails

- Work on a dedicated feature branch based on the approved integration baseline.
- Do not merge to `main` or merge dependent PRs without explicit user approval.
- Do not run production migrations or deploy as part of this design phase.
- Before any production schema change, require backup/restore verification, migration review, and a rollback/forward-fix plan.
- Document implementation decisions, tests, and device acceptance in the corresponding PR and Issue #45.

## 9. Decision summary

WF-10 is approved as the next Workforce mobile administration design/implementation track, subject to the identity/list API contract being solved first. The immediate implementation candidate is WF-10A. This document records scope and sequencing only; it does not claim implementation is complete.
