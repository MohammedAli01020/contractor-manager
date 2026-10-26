# Proposal: Future Flexible Operations & Billing

> **Status:** Draft proposal for review — not approved, not scheduled, and not implemented.
>
> This document records a possible future direction only. It does not change the product roadmap, current scope, business rules, database schema, APIs, or application behavior.

## 1. Purpose

Keep Contractor Manager expandable beyond its current equipment-and-shift workflow by separating operational records from the rules used to calculate customer charges, operator wages, and operating costs.

The current equipment-contractor focus remains the baseline. This proposal is not a recommendation to build a generalized ERP or project-management system now.

## 2. Problem to evaluate

A shift is a useful operational concept, but it is not necessarily the billing unit. For example, an excavator may work for six hours while the customer agreement charges a fixed shift rate. Actual duration, billable duration, and the agreed billing unit are distinct concepts.

Before changing the model, inspect the existing implementation and tests and determine why migration `V5__simplify_shifts_driver_status_audit.sql` removed `hours`, `quantity`, and `quantity_unit`. Do not reintroduce these fields without understanding the original design decision and its implications.

## 3. Proposed design principles

- Separate **what happened operationally** from **how it is priced and settled**.
- Keep `ShiftType` (morning/evening/night) as a scheduling category, not a billing unit.
- Preserve the current shift-based workflow and mobile-client compatibility.
- Store immutable snapshots of the rates and amounts used by a completed operation, so future rate changes do not rewrite historical financial results.
- Keep all records tenant-scoped and preserve existing tenant isolation and audit behavior.
- Prefer small, evidence-driven increments over speculative abstractions.

## 4. Billing modes to consider

These are candidates for evaluation, not committed features:

- `PER_SHIFT`: charge by shift; closest to the current workflow.
- `PER_HOUR`: charge based on validated billable duration, with explicit rules for minimum duration and rounding.
- `PER_QUANTITY`: charge by a measured unit, with a defined unit and a way to verify the completed quantity.
- `CONTRACT`: a possible future contract-level commercial model, likely requiring separate contract and work/claim records rather than adding contract behavior directly to `Shift`.

Customer charges, operator wages, and equipment operating costs may use different rules. The design should not assume they all share the same unit or rate.

## 5. Potential data concepts

Names below are illustrative only; no schema change is approved:

- Billing unit / pricing mode.
- Actual start and end timestamps where operational tracking requires them.
- Verified billable quantity and unit of measure, when applicable.
- Rate and calculated-amount snapshots for customer charges, operator wages, and costs.
- Optional links from operations to a project or contract if those concepts are later approved.

First inspect existing fields and behavior. Reuse suitable existing concepts where possible instead of duplicating them.

## 6. Possible phased approach

### Phase A — Validate the current foundation
Verify shift lifecycle behavior, tenant isolation, audit trail, financial snapshots, API behavior, mobile compatibility, and relevant tests. Review the rationale and impact of migration V5.

### Phase B — Design flexible pricing
Document the required billing rules, calculation and rounding behavior, rate history, validation, API impact, migration strategy, and backward-compatibility plan. Do not implement until the design is reviewed and approved.

### Phase C — Implement only validated needs
If approved business requirements justify it, introduce one additional billing mode at a time, with new Flyway migrations (never edit migrations already applied) and automated tests.

### Phase D — Evaluate projects and contracts separately
Only when justified by real product requirements, assess separate project, contract, work-claim, and subcontractor entities and their relationships to operations and financial records.

## 7. Guardrails

- Do not change existing behavior solely to make the architecture more abstract.
- Do not edit applied Flyway migrations; use new migrations for approved schema changes.
- Maintain tenant isolation and authorization checks on every tenant-owned resource.
- Avoid recalculating historical transactions using current rates.
- Add tests for billing calculations, rounding, missing/invalid data, status transitions, and tenant boundaries before release.
- Do not expand into full accounting, inventory, or project management without a separately approved scope.
- No implementation, roadmap scheduling, release, or merge is implied by this proposal.

## 8. Open questions for review

1. Is shift-based billing sufficient for the near-term product, or is hourly billing a confirmed customer need?
2. If hourly billing is needed, which timestamps and approval rules define billable time?
3. Are quantity-based jobs part of the target market, and which units must be supported?
4. Should operator wages and equipment costs be calculated independently from the customer billing unit?
5. When, if ever, do projects and contracts need to become first-class entities?

## Decision

**Pending owner review.** Keep this as a proposal only until the product owner explicitly approves scope and timing.
