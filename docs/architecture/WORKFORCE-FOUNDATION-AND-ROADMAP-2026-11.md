# Workforce Foundation and Product Roadmap — 2026-11

**Document status:** Consolidation and proposed product roadmap; approved direction is limited to the principles and scope explicitly marked below.  
**Workstream:** Workforce (workers, drivers, operators, supervisors, assignments, work records and compensation)  
**Documentation branch:** `docs/workforce-foundation-roadmap-2026-11`  
**Base:** `integration/stabilization-2026-11`  
**Implementation status:** This document does not implement code, create migrations, deploy, merge PRs, or modify `main`.

## 1. Product objective

Build a tenant-safe, mobile-first workforce capability for contractors. A contractor must be able to manage people in the context of sites, equipment and shifts; know who was assigned, what work was measured, what compensation was agreed, what was earned, what advances remain recoverable, and what has actually been paid.

Workforce is a core operating domain, not merely a renamed driver list. It must nevertheless evolve incrementally and preserve the existing shift, accounting and legacy-driver contracts until each integration is deliberately reviewed.

## 2. Product-owner direction and non-negotiable principles

The following are the approved architectural direction for future Workforce work:

1. **One canonical person identity per tenant.** `WorkforceMember` is the canonical workforce identity. Legacy `Driver` remains an operational/compatibility profile while dependent shift workflows are migrated safely; do not abruptly rename or drop legacy tables, IDs, APIs or ledger references.
2. **Job role is not an application permission.** DRIVER, WORKER, EQUIPMENT_OPERATOR, SUPERVISOR and other workforce roles describe work. ADMIN, OPERATOR and VIEWER remain application authorization roles. A job role must never grant login access or security permissions by itself.
3. **Keep a dated history.** Role assignments and compensation agreements are effective-dated and auditable. Do not overwrite history to represent a new assignment or rate.
4. **Freeze the commercial terms used by a shift.** The shift stores the resolved compensation basis/rate and resulting measure/amount snapshot needed to explain its earning. A later agreement change must not reprice historical work.
5. **Financial events are authoritative and append-only after posting.** Earnings, advances, payments, reversals and settlements must use the existing accounting journal and workforce subledger architecture. Never create a parallel financial ledger or silently edit posted rows/journals.
6. **Tenant isolation and concurrency are backend/database guarantees.** Tenant-safe foreign keys, authenticated tenant scope, idempotency, transactions, locking/version checks and MySQL integration tests are required; UI checks alone are insufficient.
7. **Mobile-first, Arabic/RTL-first.** Routine work must be practical on a contractor's phone. Use progressive disclosure; do not require a desktop workflow for ordinary attendance, work measurement or shift operations.
8. **Offline-first is a later delivery stage.** Prepare boundaries for offline drafts and synchronization, but do not let offline clients independently finalize approval, posting, payment, settlement or other financial/stock state transitions. Server confirmation remains authoritative.
9. **No speculative compliance claims.** Local labor, payroll, tax, insurance and e-invoicing requirements require qualified legal/accounting review before claiming compliance.

## 3. Existing implementation and evidence (do not confuse with roadmap)

The following PR history was found during this consolidation:

| Work | Evidence/status found | Boundary |
|---|---|---|
| WF-01 canonical identity foundation | PR #34 merged into `feature/financial-foundation` | WorkforceMember identity and deterministic driver backfill; legacy IDs/relationships preserved |
| WF-02 identity integration | PR #35 merged into `feature/financial-foundation` | Workforce identity boundary while retaining Driver operational profile and compatibility |
| WF-03 roles and assignments | PR #36 merged into `feature/financial-foundation`; specification exists at `docs/architecture/workforce-pr-wf-03-roles-assignments-spec-v1.md` | Tenant role catalogue, effective-dated site assignments, overlap and tenant-safety rules |
| WF-04A agreement schema/domain | PR #37 merged into `feature/financial-foundation` | Agreement/component storage and basis constraints |
| WF-04B agreement lifecycle | PR #38 merged into `feature/financial-foundation` | Draft/approve/cancel/replacement workflow, overlap checks and member-level locking |
| WF-05 shift compensation resolution | PR #39 merged into `feature/financial-foundation` | Resolve approved agreement or legacy rate and freeze shift wage snapshot; SHIFT path |
| WF-06 hourly compensation | PR #40 was found closed **without merge** | Do not assume this PR delivered hourly behavior to the integration/release line; verify current branch/code and any replacement implementation |
| WF-08 correction workflow | PRs #41–#43 are tracked separately in the current project handoff | Snapshot-based completed-shift correction; measured HOUR/QUANTITY correction and real-device acceptance remain gated as recorded in the WF-08 handoff |

**Important branch qualification:** PRs #34–#39 were merged into `feature/financial-foundation`, not into `main`. Before extending or releasing them, verify the exact ancestry and content present on the current integration/release branch. A PR marked merged into a feature branch is not proof that the code is deployed or in production.

**Existing related design:** `docs/DRIVER_WORKER_LEDGER_DESIGN.md` describes the worker ledger, earning recognition, payments, advance types and immutable financial event model. Treat it as an existing design/reference to reconcile with current code, not as proof that every described endpoint or behavior is currently shipped.

## 4. Workforce domain boundaries

### 4.1 Person identity and profile

A WorkforceMember represents the person within one tenant. It should own or coordinate the canonical identity and lifecycle. Operational profiles such as Driver may remain specialized records until all consuming workflows have migrated.

Expected profile capabilities, subject to code audit before implementation:
- display name and contact details;
- active/inactive lifecycle and deactivation reason;
- optional identity/document data with strict role-based redaction;
- tenant-scoped notes and audit metadata;
- links to legacy operational profiles without duplicate identities.

Do not store sensitive identity values in broad list responses or expose them to OPERATOR/VIEWER unless explicitly authorized.

### 4.2 Roles and assignments

Use the existing tenant role catalogue and effective-dated assignment model as the starting point. The current WF-03 specification defines common roles including DRIVER, EQUIPMENT_OPERATOR, WORKER, TECHNICIAN, SUPERVISOR, ENGINEER and ADMINISTRATIVE, with tenant-defined custom roles.

Assignments may be scoped to a site and must retain effective dates and lifecycle history. The current model intentionally does not invent project/department master-data links where those models have not been verified. Do not treat a role assignment alone as proof of shift eligibility; operational availability, equipment/site constraints and shift rules remain authoritative until a reviewed integration is implemented.

### 4.3 Work records and attendance

Separate three concepts:
- **Assignment:** where/under what role a person is expected to work.
- **Attendance/work record:** what presence or measured work actually occurred.
- **Shift:** the operational event that consumes equipment/site/worker resources and may produce revenue, cost and compensation.

Do not infer paid hours from timestamps unless a specific policy explicitly defines breaks, rounding, overnight shifts, time zones and approval. Actual hours and quantities must be explicitly captured/validated and traced to their source. Avoid creating a second attendance ledger by copying shift data without a clear ownership boundary.

### 4.4 Compensation

The agreement domain supports the intended bases:
- SHIFT — per shift;
- HOUR — per approved actual hour;
- DAY — per approved day;
- QUANTITY — per measured unit;
- FIXED — fixed amount for a defined agreement period/scope.

The existence of a basis in schema/domain does not prove its complete operational calculation, mobile UX or financial posting is shipped. Each basis needs a complete acceptance path: resolve applicable approved agreement, validate measure, calculate server-side, freeze shift/earning snapshot, post once, and support a controlled correction/reversal path.

No negative rate or unapproved agreement should be used. Preserve zero-rate semantics where explicitly allowed by the agreed operational policy. Currency must match the accounting journal's supported currency policy; do not silently convert FX.

### 4.5 Worker ledger, advances and settlements

Keep the worker subledger reconciled to the general ledger. Distinguish:
- earned payable created by completed, eligible work;
- payment against an already-earned payable;
- recoverable advance before earning (worker-advance asset);
- approved deduction/adjustment, bonus or allowance;
- settlement statement and actual cash/bank payment.

An advance is not a generic operating expense. Do not count a recoverable advance as wage expense and then deduct it again from payable. Settlement cadence should be flexible (on-demand, weekly or monthly) with optional period close, subject to a future explicit approval policy. A settlement statement is not itself a cash payment; payment is a separate confirmed event.

Use immutable journal/subledger events, source references, reason, actor, idempotency and reversal/adjustment records. Never allow a worker balance to be changed by editing a running-balance field.

### 4.6 Access and worker self-service

A worker job role must not automatically create a login. Worker login/self-service is a separate security and product decision. If introduced, start with narrowly scoped own-profile/work-history/statement access, explicit activation and recovery, tenant isolation and backend authorization. Do not expose company-wide records or allow workers to alter compensation, shifts or financial events without a separate approved workflow.

## 5. Proposed delivery roadmap (not yet implementation authorization)

The following is the recommended sequence after the relevant existing release gates. It is a planning proposal, not a claim that these stages are implemented.

| Stage | Scope | Exit criteria |
|---|---|---|
| WF-A — Foundation reconciliation | Verify which WF-01..05 commits/schema/API are present on integration; compare migrations and current code against PR descriptions; resolve duplicate/legacy paths | A verified baseline matrix with branch/commit evidence and no conflicting identity/role/agreement model |
| WF-B — Operational assignment integration | Connect WorkforceMember/role/assignment to operational shift eligibility without breaking legacy Driver APIs | Tenant-safe assignment selection; site/equipment eligibility; no duplicate person; MySQL concurrency and compatibility tests |
| WF-C — Work and attendance records | Define attendance vs shift ownership, actual time, quantity, breaks, approvals and correction lifecycle | Approved policy plus end-to-end mobile/backend tests; no inferred or duplicated hours |
| WF-D — Compensation basis completion | Complete and verify SHIFT, HOUR, DAY, QUANTITY and FIXED calculation paths as applicable | Per-basis contract tests, frozen snapshots, server-side calculations, idempotent postings and correction/reversal coverage |
| WF-E — Worker account and statement | Complete/reconcile worker ledger, own statement, opening balance policy and read permissions | Subledger-to-GL reconciliation, tenant/role tests, no parallel ledger |
| WF-F — Advances and settlements | Recoverable advances, advances against earned payable, deductions, settlement statements, approval and actual payment | No double counting, no overpayment, concurrency/idempotency, immutable postings and period-close behavior |
| WF-G — Workforce mobile experience | Arabic/RTL-first member list/profile, assignments, work capture, compensation visibility and (if approved) worker self-service | Real Android acceptance on supported Flutter version; offline drafts only where safe |
| WF-H — Reporting and release hardening | Workforce cost by site/equipment/shift, worker statements, audit exports, backup/restore and security review | Reconciled sample cases, tenant isolation, performance, restore test and documented release gate |

### Sequencing with current project priorities

- The current WF-08 completed-shift correction gate, including manual Android acceptance, remains a prerequisite before declaring that correction workstream released.
- Expense Foundation design/audit can continue without duplicating Workforce financial logic.
- Do not begin a parallel advances/payments implementation inside generic Expenses. Reconcile it with the worker ledger and the existing advances design first.
- Do not start broad Workforce mobile redesign until the backend/API baseline and real-device acceptance scope are agreed.
- Keep the current monolith. Do not introduce microservices for Workforce absent demonstrated scale or deployment needs.

## 6. Required audit before implementation

Before opening a new implementation PR, inspect the current integration branch and report exact evidence for:
1. WorkforceMember, Driver mapping, migration versions and tenant-safe constraints.
2. Role catalogue, assignment lifecycle, overlap/concurrency controls and tenant provisioning.
3. Compensation agreement schema, lifecycle, overlap rules, rate resolution and basis coverage.
4. Current shift snapshot fields and actual-hour/quantity handling in backend and Flutter.
5. Worker ledger earning/payment/advance entries, journal mappings, idempotency and MySQL immutability triggers.
6. Whether site, project, equipment and shift references are consistent and tenant-safe.
7. Worker login/self-service endpoints and actual authorization (if any).
8. Existing tests and CI runs on the exact current head; distinguish merged-to-feature, merged-to-integration, and production deployment.
9. Compatibility/migration and rollback implications; never edit an already-applied migration.
10. Real Android workflow evidence for all claimed mobile capabilities.

## 7. Decisions still requiring focused approval

Do not silently decide these while coding:
- Which worker categories require a Driver operational profile versus a generic workforce profile.
- Whether attendance is an independent first-class record or shift-only for the initial commercial release.
- Exact day/hour rules: breaks, rounding, overtime, overnight shifts, holidays and approval responsibility.
- Which compensation bases are enabled for launch and whether FIXED is per period, assignment or deliverable.
- Whether assignment-level compensation overrides member-level compensation and the precedence when multiple sites/assignments could match.
- Opening balance/import policy for existing worker balances.
- Advance limits, approval thresholds, recovery ordering, hardship/exception handling and whether recovery can cross settlement periods.
- Settlement cadence, approval roles, payment proof and period-close lock behavior.
- Whether workers receive login access at launch, and the activation/recovery method.
- Applicable Egyptian labor, payroll, insurance, tax and e-invoice requirements, subject to qualified professional review.

## 8. Acceptance and release gates

A Workforce release is not complete until all applicable checks pass on the exact proposed commit:
- backend and Flutter CI completed successfully;
- MySQL/Testcontainers migrations and integration tests execute with zero unexpected skips;
- tenant isolation and role authorization, including cross-tenant negative tests;
- concurrent duplicate/retry/payment/assignment tests and stable idempotent outcomes;
- accounting journal and worker subledger reconcile exactly;
- posted records cannot be edited/deleted; corrections are reasoned, auditable reversals/adjustments;
- no duplicate expense, earning, advance recovery or payment;
- API compatibility and migration/rollback plan reviewed;
- backup/restore rehearsal before production data migration;
- actual Android acceptance for the mobile workflows in scope;
- documentation and operator/release instructions updated.

## 9. Change control

- This document consolidates existing evidence and proposes a future sequence; it does not retroactively approve every historical design statement as shipped behavior.
- A stage becomes approved for implementation only after its scope, policy decisions, files/schema/API changes and acceptance tests are reviewed.
- No PR merge, production migration, deployment or `main` change is authorized by this document.
- Update implementation status only from verified code, tests and GitHub state on the exact commit.
