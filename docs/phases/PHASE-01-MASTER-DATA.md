# Phase 01 — Master data and shift usability

**Roadmap:** [PRODUCT_ROADMAP.md](../PRODUCT_ROADMAP.md#phase-1--driverequipment-master-data-and-shift-usability)  
**Status:** In progress — compare the existing implementation with the criteria below.

## Goal
Provide reliable, tenant-scoped management of sites, drivers/workers, equipment types, and equipment, with clear availability and safe historical shift snapshots.

## Existing API baseline
All routes are expected under `/api/v1`; confirm exact paths and permissions against the current controllers before changing them.

| Resource | Collection | Known filters |
|---|---|---|
| Sites | `/sites` | `q`, `includeInactive` |
| Drivers/workers | `/drivers` | `q`, `type`, `includeInactive` |
| Equipment types | `/equipment-types` | `includeInactive` |
| Equipment | `/equipment` | `q`, `typeId`, `status`, `includeInactive` |

## Rules to verify
- [ ] List endpoints paginate consistently; page starts at zero and maximum page size is bounded.
- [ ] Updates require the latest `version`; stale updates return HTTP 409.
- [ ] Delete/deactivate operations preserve historical records; inactive records can be queried and reactivated only through supported flows.
- [ ] Monetary values use the agreed decimal precision and reject negative values in both API validation and the database where appropriate.
- [ ] Site names, equipment-type names, and equipment codes are unique within a tenant, case-insensitively where specified.
- [ ] Equipment type changes/deactivation do not invalidate active equipment references.
- [ ] Driver availability and equipment status are clear in the UI; unavailable resources cannot be selected for a new shift.
- [ ] An active shift prevents incompatible resource status changes or deactivation.
- [ ] Rate/default changes affect future shifts only; historical shift snapshots remain unchanged.
- [ ] Tenant isolation is enforced server-side and covered by tests.
- [ ] Flutter date/resource selection remains compatible with the repository's current Flutter/Dart constraints.

## Acceptance criteria
- [ ] A contractor can create, view, update, deactivate, and reactivate supported master-data records.
- [ ] Validation, duplicate conflicts, stale-version conflicts, and tenant boundaries have tests.
- [ ] Shift selection reflects current driver/equipment availability.
- [ ] Backend tests and Flutter analyze/tests pass for the touched scope.

## Evidence and next action
Record the current commit, commands, results, and blockers after verifying the existing implementation. Do not infer completion merely because endpoints or tests exist.
