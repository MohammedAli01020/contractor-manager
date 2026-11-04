# PR-WF-04 — compensation agreements and legacy shift-rate conversion

**Status:** Detailed implementation specification; not implemented.  
**Depends on:** PR-WF-02 canonical identity and PR-WF-03 roles/assignments being reviewed, merged into the financial foundation, and revalidated.  
**Design source:** `workforce-compensation-and-hr-design-v1.md`.  
**Safety:** No application code, database migration, production data, journal, payable ledger, or `main` branch is changed by this specification.

## 1. Objective and strict scope

Introduce an auditable, effective-dated compensation agreement model that can represent SHIFT, HOUR, DAY, QUANTITY, and FIXED compensation. MIXED compensation is represented by multiple independent components in one agreement.

The first production-enabled use case is migration of existing driver/worker shift rates (morning, evening, night) into SHIFT components. The other bases are represented in the schema but are not enabled for earning calculation or payroll posting in this PR.

PR-WF-04 does **not**:
- change ShiftService rate resolution or shift start/finish behavior (PR-WF-05)
- change driver wage recognition, journal posting, driver ledger, recoverable advances, or payments
- recalculate or rewrite any historical shift, journal, or ledger entry
- implement attendance, timesheets, quantity approval, payroll runs, or non-shift posting
- remove legacy driver rate columns
- add Flutter screens or apply a production migration

## 2. Core model

### 2.1 CompensationAgreement

Tenant-scoped agreement header:
- id, tenant_id
- workforce_member_id (required)
- workforce_assignment_id (required for the first release; same tenant and same member)
- agreement_reference (optional tenant-unique reference)
- status: DRAFT, APPROVED, SUPERSEDED, EXPIRED
- effective_from (inclusive), effective_to (inclusive, nullable)
- currency_code (tenant currency; do not silently mix currencies)
- source: MANUAL, LEGACY_DRIVER_RATE_MIGRATION, IMPORT
- approved_by_user_id, approved_at (required when APPROVED)
- created_by_user_id, updated_by_user_id, timestamps, optimistic version

An agreement is bound to a specific workforce assignment so a member with multiple roles can have different terms for each role. The first legacy conversion links DRIVER or WORKER operational profiles to the matching role assignment. A member-level unscoped agreement is deliberately not supported in this release; future general agreements must define their role/scope semantics explicitly.

Approved agreements are immutable in their effective terms and components. A correction or rate change creates a new agreement and supersedes/closes the prior agreement with a traceable actor and timestamp. Draft agreements may be edited or cancelled. No hard deletion after approval.

### 2.2 CompensationComponent

Each agreement has one or more components:
- id, tenant_id, compensation_agreement_id
- component_code and display_name
- basis: SHIFT, HOUR, DAY, QUANTITY, FIXED
- rate DECIMAL(15,2), nonnegative
- unit_code (required for HOUR, DAY, QUANTITY; optional for SHIFT/FIXED)
- shift_type: MORNING, EVENING, NIGHT (required only for SHIFT)
- optional quantity precision/rounding policy metadata, reserved but not used for calculation in this PR
- sort_order, timestamps, optimistic version

Validation:
- SHIFT requires exactly one shift_type and no duplicate (agreement, component_code/shift_type) entry.
- HOUR/DAY/QUANTITY require a unit; FIXED requires an explicit period unit (e.g. MONTH or WEEK) before calculation can be enabled.
- rate is DECIMAL and >= 0; floating-point is forbidden.
- Zero is a configured value, not a missing value. This migration preserves zero rates as zero. Existing shift completion validation still rejects a non-positive wage; PR-WF-04 does not change that behavior or infer that zero is payable.
- A SHIFT agreement used for current driver operations must contain exactly one MORNING, one EVENING and one NIGHT component. Missing or duplicate components fail validation; do not fill them with guesses.

MIXED is not a basis enum value. It is an agreement with multiple components, for example a monthly fixed component plus approved overtime hours or quantity.

## 3. Effective dates, scope and concurrency

- Business dates use `LocalDate` in the configured tenant/business timezone; do not store effective dates as UTC instants.
- Start and end dates are inclusive.
- An approved agreement cannot overlap another approved agreement for the same workforce assignment and compensation basis/component key. For SHIFT, the key includes shift_type; for other bases it includes basis and unit/component code.
- Different components in the same agreement may coexist (the point of MIXED).
- The service locks the WorkforceMember first, then assignment, then matching agreement rows in a stable order before checking overlap and writing. All write paths use the same lock order.
- Database constraints enforce tenant-safe member, assignment, user and component/agreement references, date validity and allowed statuses. Cross-row overlap is checked transactionally under locks; a plain unique index is not sufficient for date-range overlap.
- Assignment and agreement must belong to the same tenant and the same WorkforceMember. Assignment must be valid for the agreement's effective range; an agreement may not extend beyond the assignment's end date.
- A role assignment cannot be ended before the end of its approved compensation agreement. The assignment-ending service must reject this with a conflict; extending/ending the agreement is a separate audited action.

## 4. Legacy conversion strategy

### 4.1 No automatic historical backdating

Do not use driver creation date, first shift date, or current system date as an assumed contractual effective date. The repository only proves the current values in `drivers.morning_rate`, `evening_rate`, and `night_rate`; it does not prove when those values became effective.

Each tenant requires an explicit cutover business date selected during release preparation. The cutover date is a per-tenant operational decision, not a hidden SQL default. The conversion command must require the date and refuse to run if it is absent or earlier than the approved release date.

### 4.2 Preflight report (read-only)

Before writing anything, produce a tenant-scoped report containing:
- active and inactive driver rows and their `workforce_member_id`
- `worker_type` (DRIVER or WORKER)
- all three legacy rates including zero values
- existing role assignments that cover the cutover date
- duplicate/overlapping same-role assignments
- existing approved agreements, if any
- missing identity/role mapping, invalid assignment date range, or duplicate conversion candidates
- expected agreement/component/assignment counts and a checksum or stable row-key summary

Do not silently skip an anomalous driver. Any unresolved anomaly blocks conversion for that tenant and produces a clear remediation list.

### 4.3 Role assignment mapping

For each legacy operational profile:
- `worker_type = DRIVER` maps to the tenant's DRIVER role.
- `worker_type = WORKER` maps to the tenant's WORKER role.
- Find the one assignment for the same WorkforceMember/role that covers the cutover date.
- If none exists, create one cutover assignment beginning on that date, using the existing active state as the assignment lifecycle input; do not invent a project/site.
- If more than one candidate covers the date, stop and require manual resolution. Do not pick one arbitrarily.
- Do not create a duplicate assignment when a valid matching assignment already exists.
- A role assignment is not a login permission and does not change Driver operational eligibility.

### 4.4 Agreement/component conversion

For each resolved legacy operational profile, create one approved SHIFT agreement tied to its matching assignment:
- MORNING component = `drivers.morning_rate`
- EVENING component = `drivers.evening_rate`
- NIGHT component = `drivers.night_rate`
- source = LEGACY_DRIVER_RATE_MIGRATION
- effective_from = explicit tenant cutover date
- effective_to = null, unless a pre-existing approved successor agreement requires a controlled close
- preserve zero values exactly

The conversion must be idempotent with a deterministic tenant/profile/cutover idempotency key. A retry after partial infrastructure failure must not duplicate assignments, agreements or components. If a row was manually changed after a failed run, stop and reconcile rather than overwrite.

### 4.5 Postflight reconciliation

After conversion, produce and retain:
- count of eligible legacy profiles vs converted agreements
- count of exactly three SHIFT components per agreement
- exact per-driver equality of all three source rates and converted component rates
- proof that each agreement links to the correct member and role assignment
- zero missing, duplicate or cross-tenant references
- no changes to existing shift wage snapshots, shift statuses, journal entries, journal lines, driver ledger entries, or balances

A conversion is not considered successful until preflight and postflight reports are reviewed and signed off.

## 5. Legacy compatibility boundary

- Keep `drivers.morning_rate`, `evening_rate`, and `night_rate` unchanged as monitored compatibility values during PR-WF-04.
- PR-WF-04 does not write new values back from agreements into legacy columns.
- Until PR-WF-05 is deployed, current ShiftService remains the source of rate selection from legacy columns.
- PR-WF-05 must resolve the agreement by shift date and shift type, snapshot agreement/component/rate on new shifts, and only use legacy rates as an explicit monitored fallback while a tenant's conversion status is not complete.
- Do not permit a silent fallback after cutover if the agreement is missing or ambiguous; raise an operational alert/error according to the approved rollout policy.
- Existing ShiftRequest permits a caller-supplied driverWage override. PR-WF-05 must explicitly restrict this to authorized roles, record the override reason and audit event, and ensure the resulting snapshot is visible. PR-WF-04 leaves that API behavior untouched.
- Never backfill agreement/component references onto historical shifts. Historical `driver_wage` and posted journal/ledger records remain the source of truth.

## 6. Accounting and payroll boundaries

- Agreement approval creates no journal, payable, expense, or cash movement.
- A compensation rate is a contractual term, not an earned amount.
- Current shift completion remains the sole recognition event for the current driver shift wage, with the existing journal + driver ledger atomic transaction.
- No duplicate worker payable ledger is introduced.
- Advances remain separate from compensation agreements and wage expense.
- Non-shift HOUR/DAY/QUANTITY/FIXED components cannot generate earnings or accounting postings until separate source-evidence, payroll approval, idempotent posting and reconciliation work is implemented.

## 7. API boundary

Proposed ADMIN-only write APIs:
- GET /api/v1/workforce/members/{memberId}/compensation-agreements
- POST /api/v1/workforce/members/{memberId}/compensation-agreements (create DRAFT)
- PUT /api/v1/workforce/compensation-agreements/{agreementId} (DRAFT only, optimistic version)
- POST /api/v1/workforce/compensation-agreements/{agreementId}/approve
- POST /api/v1/workforce/compensation-agreements/{agreementId}/supersede (requires successor/effective date)
- GET /api/v1/workforce/compensation-agreements/{agreementId}

Read access follows the existing workforce read policy; sensitive financial agreement details should not be exposed to VIEWER unless product permissions explicitly allow it. Do not reuse login role names as job roles.

The conversion command is an internal/admin-controlled operation, not a public tenant API endpoint.

## 8. Required MySQL integration tests

- V38 schema migration against populated tenant/driver/assignment data.
- tenant-safe FKs: foreign tenant member, assignment, actor or component is rejected.
- assignment/member mismatch is rejected.
- invalid effective date range is rejected.
- duplicate/overlapping approved agreement components are rejected under concurrent transactions.
- distinct shift types coexist; duplicate same shift type is rejected.
- draft can be edited; approved agreement/components cannot be edited or deleted.
- approval requires all three shift components for legacy SHIFT agreement.
- superseding agreement closes prior range and does not mutate prior component/rate snapshots.
- zero rate is retained as zero; missing/null rate is not silently converted to zero.
- preflight blocks missing identity, ambiguous role assignment and duplicate candidate rows.
- conversion retry is idempotent; partial failure recovery does not duplicate data.
- conversion preserves exact rate equality and leaves all historical shift/journal/ledger data unchanged.
- agreement approval alone creates no journal, payable or cash movement.
- existing shift, wage-zero, recovery, accounting idempotency and tenant-isolation tests remain green.

CI must explicitly require the new compensation integration test suite and migration report checks to execute without skips.

## 9. Implementation breakdown

Recommended PR-WF-04 delivery slices:
1. **PR-WF-04A — schema and domain:** V38 tables, entities, repositories, tenant-safe constraints, validation; no conversion and no shift integration.
2. **PR-WF-04B — agreement APIs:** draft creation/editing, approval, supersession, overlap/concurrency rules, authorization and audit.
3. **PR-WF-04C — legacy conversion tooling:** read-only preflight, explicit per-tenant cutover date, deterministic/idempotent conversion, postflight reconciliation. Run first in report-only mode; production execution requires separate approval.
4. **PR-WF-05 — shift resolver integration:** separate PR after 04A/B and conversion are validated; preserve wage snapshots and journal/ledger behavior.

If keeping all of 04A/B/C in one PR would make review too large, split them as above. Do not start 04C production conversion until the preflight report has been reviewed.

## 10. Open product-owner decisions before production conversion

The following are deliberately not guessed:
- cutover business date per tenant
- tenant currency source and whether multi-currency worker agreements are ever allowed
- whether zero-rate shifts should remain unfinishable (current behavior) or be supported through an explicit policy
- exact permissions for OPERATOR/VIEWER to read compensation terms
- whether the first rollout enables only SHIFT agreements (recommended) or also exposes draft non-shift components in the UI

Default recommendation: enable SHIFT agreements only in the first rollout; store the other bases in the extensible model but do not calculate or post them yet.
