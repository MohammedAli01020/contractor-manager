# WF-12A — Workforce to Shift Eligibility

## Status

Implementation in progress on this branch. Backend, Flutter, and integration-test changes have been added; CI validation is pending.

## Confirmed current-state gap

The mobile shift editor loads its people selector from `GET /api/v1/live/drivers`. That endpoint is implemented by `LiveStatusService.drivers` and currently enumerates only active rows in the legacy `drivers` table.

WF11A/WF11B create canonical `workforce_members` and `workforce_assignments`, but onboarding does not create a linked operational `drivers` extension. Consequently, a newly onboarded person with an active DRIVER, EQUIPMENT_OPERATOR, or WORKER role can appear in Workforce and still be absent from the shift selector.

This is not a backend restart issue and not a Flutter refresh issue. It is a missing integration between canonical role assignments and the existing shift resource model.

## Existing compatibility constraints

- `drivers.workforce_member_id` is already a required, tenant-scoped link to the canonical Workforce identity (V36).
- `shifts.driver_id` is the current operational foreign key and is used by live-status queries, shift snapshots, wage resolution, and legacy driver-ledger references.
- `ShiftCompensationResolver` resolves agreements through `Driver.workforce_member_id`; it retains legacy driver rates only as a compatibility fallback when no approved agreement exists.
- The current shift editor selects one driver/worker per shift. It does not yet support a full crew of multiple workers.
- Workforce role assignments carry date and optional site scope; eligibility must honor those fields, not simply show every active person.

## WF-12A target behavior

1. Workforce remains the canonical identity and role source.
2. A shift-eligible person must have an active Workforce identity and a role assignment effective on the shift date:
   - `DRIVER` or `EQUIPMENT_OPERATOR` qualifies for the operator slot.
   - `WORKER` qualifies for the worker slot.
   - `SUPERVISOR` and `ENGINEER` do not automatically qualify as the shift's driver/operator.
3. If an operational driver extension is required by the current shift schema, it is a one-to-one extension linked to the Workforce member, not a second person identity. Do not create duplicate identities.
4. Preserve existing `driver_id` values and historical shifts. Do not rename/drop `drivers`, `driver_ledger_entries`, or `shifts.driver_id` in this phase.
5. Availability is derived from active shifts plus the operational availability flag. A person already working must not appear as free.
6. Site-scoped assignments must match the selected site; global assignments (no site) may qualify for any site.
7. Wage resolution remains independent of equipment costing and is based on the person's approved, effective-dated Workforce compensation agreement. Never infer a wage basis from the equipment's basis.
8. No silent fallback to a different person or role when assignment eligibility is missing or ambiguous.

## Required implementation slices

### WF-12A.1 — Operational extension lifecycle

- Define the exact minimal extension fields needed by shift operations: legacy worker classification, availability/unavailability reason, and compatibility rates only where legacy fallback is still supported.
- Add an explicit, idempotent service to provision or reconcile the extension for an already-existing Workforce identity when an eligible operational role is assigned.
- Ensure role assignment changes and extension provisioning are transactional; enforce one extension per tenant/member.
- Existing legacy drivers remain linked to their current Workforce identity without rewriting IDs.
- Do not populate compensation rates from guesses. New members without an approved agreement must not silently receive a fabricated wage. Any compatibility rate default must be explicitly represented as zero and surfaced as such until an agreement exists.

### WF-12A.2 — Eligible candidate API

- Add a tenant-scoped endpoint that accepts shift date and site, and returns eligible people with Workforce member ID, operational driver ID, display name, role, assignment ID/site, and live availability.
- Batch-load assignments, identities, operational extensions, and active-shift occupancy; avoid N+1 queries.
- Keep national ID and private notes out of the response.
- Return clear empty-state and ineligibility information; do not expose inactive people as selectable.

### WF-12A.3 — Shift editor integration

- Load candidates after date and site are known.
- Filter the picker by operational slot (operator/driver vs worker) and availability.
- Preserve edit behavior for the person already attached to the current shift.
- Keep the persisted legacy `driver_id` compatibility reference until a separately reviewed schema migration introduces a canonical shift-member reference.
- Add Flutter tests for role filtering, site/date eligibility, unavailable/working status, and current-shift editing.

### WF-12A.4 — Shift crew model (separate follow-on)

The present shift schema supports only one `driver_id`. A shift with a driver plus several workers requires a separate shift-crew/assignment model and financial allocation rules. Do not overload `driver_id` or pretend the current selector supports multiple workers. Design this only after the single responsible operator path is correct.

## Acceptance criteria

- A newly onboarded member with a valid DRIVER or EQUIPMENT_OPERATOR assignment can be made shift-eligible without creating a duplicate Workforce identity.
- A WORKER is represented distinctly and is not mislabeled as a driver.
- The candidate list respects tenant, role, assignment effective dates, site scope, active identity, and current shift occupancy.
- Starting a shift persists the correct existing operational reference and compensation is resolved against the linked Workforce identity.
- Existing historical shifts, driver ledgers, and compensation snapshots remain unchanged.
- Backend integration tests cover tenant isolation, date/site filtering, role eligibility, duplicate extension prevention, and transactional rollback.
- Flutter tests cover the new candidate API and picker behavior.
- No production database rebuild or volume deletion is permitted; any migration must be additive, reviewed, and tested against an isolated database.

## Out of scope

- Equipment cost agreements and equipment ownership lifecycle.
- Multi-worker crew assignment (separate follow-on).
- Renaming or dropping legacy driver tables or ledger tables.
- Changes to posted journals or historical wage recalculation.
