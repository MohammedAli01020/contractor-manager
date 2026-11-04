# Workforce compensation and HR foundation — detailed design decisions

**Status:** Proposed design baseline for review; not implemented  
**Date:** 2026-11-04  
**Source branch reviewed:** `feature/financial-foundation` at `e03e49b1fbe882e08dde702acb7a9ff3fcd05c84`  
**Related:** `workforce-legacy-dependency-inventory-v1.md`, `workforce-core-migration-proposal-v1.md`

## 1. Purpose

Resolve the compensation, role, and legacy-rate questions before implementation. The design must support contractors with drivers, equipment operators, laborers, technicians, supervisors, engineers, and administrative staff without forcing every employee into the shift-driver workflow.

This document is a technical design proposal. It does not authorize schema or application changes.

## 2. Canonical workforce identity and roles

### 2.1 Workforce member

One `workforce_member` represents one real person within one tenant. Person identity is independent of job, assignment, operational eligibility, and login account.

Identity data:
- Full name (required)
- Phone (optional)
- National ID (optional; preserve existing values, restrict read access, do not expose in list/search payloads by default)
- Notes (optional)
- Active/inactive lifecycle status
- Standard audit/version fields

Do not hard-delete a person who has shifts, ledger movements, payroll, or audit history. Deactivation blocks new assignments/work but preserves all history.

### 2.2 Assignments and role catalog

Use tenant-owned role definitions with stable role codes and labels. Seed common roles (DRIVER, EQUIPMENT_OPERATOR, WORKER, TECHNICIAN, SUPERVISOR, ENGINEER, ADMINISTRATIVE) while allowing a tenant to add roles without a code deployment.

A workforce member may have multiple assignments, including overlapping assignments where operationally valid. Each assignment has:
- role
- effective start/end dates
- optional department, project, site, supervisor
- status and audit metadata

Do not model a person's role as one enum on the person record. A person may be a driver on one project and a supervisor on another, or hold historical roles over time.

Operational eligibility is separate from role label. The current driver/operator profile remains the first specialized operational profile. Do not make every workforce member selectable in shift creation merely because they have a workforce identity.

### 2.3 Login account

Keep `users` separate. A workforce member may have no login, and a login may be an owner/accountant/dispatcher who is not an employee. Optional account linking can be a later feature with explicit role/permission assignment and audit.

## 3. Compensation agreement model

Use an effective-dated agreement header plus one or more typed components. Avoid a single generic rate column and avoid storing MIXED compensation as an opaque formula string.

### 3.1 Agreement header

Suggested fields:
- id, tenant_id, workforce_member_id
- agreement number/reference and status (DRAFT, APPROVED, SUPERSEDED, EXPIRED)
- effective_from, effective_to
- currency
- optional assignment_id, project_id, site_id
- approval metadata (approved_by, approved_at)
- created_by, created_at, updated_at, version
- source marker for migration/audit (MANUAL, LEGACY_DRIVER_RATE_MIGRATION, IMPORT)

No overlapping APPROVED agreements for the same worker, compensation scope, and component/basis unless an explicit priority/scope rule resolves the overlap. Validate overlap transactionally while locking the workforce member. Date boundaries are inclusive and timezone-independent (business dates).

### 3.2 Agreement components

Each agreement has one or more components with:
- component code/name
- basis: SHIFT, HOUR, DAY, QUANTITY, FIXED
- rate/amount DECIMAL(15,2), nonnegative
- unit where applicable (hour, day, ton, meter, piece, trip, etc.)
- optional shift type (MORNING, EVENING, NIGHT) for SHIFT basis
- optional quantity unit and rounding rule
- optional minimum/maximum or guaranteed amount only where the business rule explicitly requires it
- earning category/accounting mapping and taxable/statutory metadata reserved for future localization

MIXED is not a component basis. It is an agreement containing multiple components, for example fixed monthly base + overtime hours + per-unit production. This allows each part to be calculated, explained, approved, and audited separately.

### 3.3 Basis semantics

- SHIFT: rate per completed, eligible shift; may have different components for morning/evening/night. Recognition remains in the existing shift completion workflow for the current driver operation.
- HOUR: approved eligible hours multiplied by rate. Require a defined source of hours, approval and rounding policy; never infer hours from clock timestamps without an approved attendance rule.
- DAY: approved payable days multiplied by rate; define treatment of partial days explicitly before enabling.
- QUANTITY: approved quantity multiplied by rate and unit. Quantity source must be linked to an approved production/work record or approved payroll input, not arbitrary free text alone.
- FIXED: fixed amount for a defined period (usually monthly, potentially weekly). Proration, joining/leaving mid-period, unpaid absence and partial period policy must be configured before production use.
- MIXED: multiple independent components in one agreement, each with its own basis and source evidence.

All monetary calculations use decimal arithmetic and the tenant currency precision. Never use floating point for money. Persist calculation inputs, rate/version, formula version, and calculated amount so a later rate change cannot rewrite a prior calculation.

## 4. Mapping the existing three shift rates

Current source has `drivers.morning_rate`, `evening_rate`, and `night_rate` as current defaults. Existing `ShiftService` selects the applicable rate when creating a shift; the shift stores `driver_wage` as a snapshot and recognizes it on completion. The existing schema also has optional shift hours and quantity fields, but those fields alone do not establish a general hourly or piecework payroll process.

Recommended migration:
1. For each existing driver, create one workforce member and one driver operational profile link.
2. Create one approved legacy-derived SHIFT agreement effective from the agreed cutover business date, with three components:
   - MORNING -> existing morning_rate
   - EVENING -> existing evening_rate
   - NIGHT -> existing night_rate
3. Record source = LEGACY_DRIVER_RATE_MIGRATION and retain the original driver row values for audit/reconciliation.
4. Do not backdate the agreement to the driver's creation date: the system does not prove that today's rate was historically effective throughout the past.
5. Do not recalculate old shifts. Their stored `driver_wage` and posted journal/ledger rows remain the historical source of truth.
6. For shifts created after cutover, resolve the applicable approved agreement and snapshot the selected component/rate and agreement ID on the shift. During the compatibility period, preserve old driver rate fields as a fallback only; emit monitoring when fallback is used. Remove fallback only after all tenants have valid agreements and reconciliation passes.

If a driver has zero rates, migrate zero as a configured rate only if zero is valid under the current product policy; do not silently treat it as missing. Existing completion validation remains responsible for whether a zero wage can be posted.

## 5. Earnings, payable, payroll, and payment are separate stages

The system must show three different concepts clearly:

1. **Agreed compensation:** contractual rate/terms effective for a date.
2. **Recognized earning / payable:** amount earned after its source event is validated and approved/posted.
3. **Paid amount:** cash/bank settlement actually made.

Do not use one amount or status to represent all three.

### 5.1 Existing shift earnings

For the current driver shift workflow:
- creating/starting a shift does not recognize wage expense
- finishing a valid shift recognizes its wage exactly once
- shift completion writes the journal and worker payable movement atomically
- the shift stores its wage/rate snapshot and journal reference
- later payroll may display/include that earning for the period, but must mark it ALREADY_RECOGNIZED and must not create another wage journal or earning movement
- payment reduces payable through the existing payment flow; it is not a second wage expense

### 5.2 Non-shift earnings

For HOUR, DAY, QUANTITY, and FIXED:
- capture source evidence (approved timesheet, attendance/day record, production/quantity record, or fixed-period agreement)
- calculate a draft payroll run
- show component-level calculation details and exceptions
- supervisor/payroll approver reviews and approves
- approval posts one wage journal and one payable movement per approved earning source/component with idempotency protection
- payment is a separate settlement and can be partial or grouped, with allocation to worker payable movements

Draft, preview, recalculation, attendance capture, and rejected payroll runs have no general-ledger effect.

### 5.3 Payroll period and run

PayrollRun is a workflow/calculation container, not a parallel worker balance ledger. It should store:
- tenant, period start/end, status (DRAFT, CALCULATED, UNDER_REVIEW, APPROVED, POSTED, CANCELLED)
- included source IDs and source versions
- totals by worker/component/account
- calculation warnings/exceptions
- approval/posting metadata and idempotency token

The authoritative outstanding balance remains the worker payable subledger. Payroll reports aggregate source recognition and settlement; they do not maintain another independently mutable balance.

A run must not be posted until all included sources are locked or version-checked. Recalculation after approval is forbidden; corrections use reversal/adjustment entries and a replacement run.

## 6. Advances, deductions, and corrections

- Advance against earned payable: a payment/advance against an existing earned balance; decreases outstanding payable and posts the cash/bank movement.
- Recoverable advance: a receivable from the worker; increases recoverable advance asset and is recovered from later approved earnings through a separately traceable recovery movement.
- A recovery reduces the worker's net payable and the receivable; it is not a new wage expense.
- Other deductions (absence, damage, loan installment, penalty, statutory deduction) must have separate types, authorization, source evidence, effective date and accounting mapping. Do not encode all deductions as negative wages.
- Any correction after posting uses reversal and replacement movements; never mutate posted journal/ledger history.

## 7. Operational workflow boundaries

- Driver/operator shift remains a specialized workflow with equipment, site, shift type, availability, active-slot uniqueness, and shift wage snapshot.
- General workers can have hourly/day/quantity/fixed compensation without being forced into an equipment shift.
- Supervisors/engineers/admin staff may be assigned to projects/sites and may have fixed or mixed compensation without being added to the shift driver dropdown.
- A later attendance module may capture time and leave, but attendance is evidence/input. It is not automatically an accounting expense.
- The first HR release should cover worker profile, role assignments, active/inactive lifecycle, and compensation agreement setup. Full attendance, leave, payroll processing, statutory rules and self-service can follow in separate releases.

## 8. Recommended implementation sequence

1. Workforce member identity and one-to-one legacy driver mapping.
2. Role catalog and effective-dated assignments.
3. Compensation agreement header/components and legacy rate migration.
4. Resolve shift rates through agreements while preserving wage snapshots and driver fallback.
5. Workforce-aware payable subledger and journal dimension, with reconciliation.
6. Non-shift earning source records and draft payroll calculation.
7. Approval/posting for non-shift earnings, then cash/bank settlement allocation.
8. Attendance, leave, deductions, and richer HR capabilities in later releases.

Each stage requires a preflight report, MySQL integration tests, tenant-isolation tests, financial reconciliation, mobile/API compatibility checks, and explicit release review.

## 9. Acceptance scenarios

- Legacy driver maps to one and only one workforce member; tenant boundary enforced.
- Three legacy rates map to the correct shift types at cutover; old shift wage snapshots remain unchanged.
- Agreement rate change tomorrow does not alter yesterday's shift or posted wage.
- Overlapping approved agreements for the same scope are rejected unless an explicit priority rule applies.
- One person may hold multiple roles without creating duplicate payable balances.
- A non-driver hourly worker can accrue approved hours without being offered as a shift driver.
- A quantity worker's earning links to approved production quantity and rate version.
- A fixed salary is calculated for a defined period with explicit proration policy.
- A mixed agreement produces explainable component-level amounts.
- Payroll includes shift earnings as already recognized and never posts them twice.
- Payroll approval is idempotent under retry/concurrency.
- Partial payments, recoverable advance and recovery reconcile to the single worker payable subledger.
- Posted corrections create reversal/replacement lineage and never mutate the original journal.
- Tenant A cannot read, assign, pay, or post against Tenant B's workforce members or agreements.

## 10. Decisions recommended for approval

The recommended choices in this document are:
- migrate today's three rates as a SHIFT agreement effective at cutover, not backdated
- keep old rate columns as temporary compatibility fallback and remove them only in a later release
- model MIXED as multiple components, not a special formula string
- make assignments multi-role and effective-dated
- defer full attendance/leave and statutory payroll localization until after workforce core
- use payroll as calculation/approval/reconciliation, never as a second balance ledger
- preserve current shift completion as the recognition event for current shift wages

No application code, database migration, API, PR, or `main` branch was changed by this design work.
