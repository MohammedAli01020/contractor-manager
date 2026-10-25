# Phase 00 — Stabilize and verify the existing core

**Roadmap:** [PRODUCT_ROADMAP.md](../PRODUCT_ROADMAP.md#phase-0--stabilize-and-verify-the-existing-core)  
**Status:** In progress — refresh branch and test evidence before acceptance.

## Goal
Verify that the existing backend, database constraints, and Flutter client behave reliably before expanding product scope.

## Scope and checklist
- [ ] Inspect the current branch, working tree, API contracts, Flyway migrations, and relevant tests before changing code.
- [ ] Verify shift create/start, list/filter, update, finish, cancel, and reinstate behavior where supported.
- [ ] Verify the invariant that each driver and each equipment item can have at most one `IN_PROGRESS` shift at a time, regardless of shift date.
- [ ] Verify the invariant at the database level, including concurrent requests and direct database writes where applicable; service pre-checks alone are not sufficient.
- [ ] Verify that finishing/cancelling a shift releases the driver/equipment for a later shift and that a forgotten active shift blocks a conflicting start.
- [ ] Verify optimistic-version conflicts and the intended HTTP 409 behavior.
- [ ] Verify that a driver/equipment cannot be deactivated or moved into an incompatible status while an active shift references it.
- [ ] Verify tenant isolation for all touched endpoints and relationships.
- [ ] Run backend tests and Flutter analyze/tests; record exact commands and actual outcomes below.
- [ ] Preserve existing shift snapshots, including shift price, driver wage, and operating-cost estimate. Do not remove `operatingCostPerShift`, `equipmentOperatingCost`, or their API equivalents as part of this phase.

## Relevant existing shift API (re-check against code before changing)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/shifts` | Start a shift |
| POST | `/api/v1/shifts/{id}/finish` | Finish a shift |
| PUT | `/api/v1/shifts/{id}` | Update a shift |
| POST | `/api/v1/shifts/{id}/cancel` | Cancel a shift |
| POST | `/api/v1/shifts/{id}/reinstate` | Reinstate where allowed |
| GET | `/api/v1/shifts` | Filtered shift list |
| GET | `/api/v1/shifts/running` | Active shifts |
| GET | `/api/v1/shifts/{id}/history` | Shift history |

The paths above are a documentation baseline, not a substitute for checking the current controller/API contract.

## Resource status, live lists, and audit behavior to verify
- [ ] Driver availability uses the supported `AVAILABLE` / `UNAVAILABLE` states and a reason when required; equipment status uses the supported `AVAILABLE`, `MAINTENANCE`, and `OUT_OF_SERVICE` states.
- [ ] A driver or equipment item with an active shift cannot be deactivated or moved into an incompatible state; verify the conflict response.
- [ ] “Working now” is derived from active shifts rather than maintained as a second independent state.
- [ ] Verify the live endpoints `GET /api/v1/live/equipment` and `GET /api/v1/live/drivers`, including `onlyFree` behavior and the context shown for active resources, against the actual controller contract.
- [ ] Verify `GET /api/v1/audit` is tenant-scoped, paginated, newest-first, and records supported changes with actor/time in the same transaction as the operation.
- [ ] Audit records never store passwords, access tokens, or refresh-token secrets. Confirm actual audit scope from code; do not assume every entity/action is currently covered.

## Acceptance criteria
- [ ] No two active shifts can use the same driver or equipment, including under concurrent requests.
- [ ] Database constraints/triggers and application behavior agree; database failures are translated into stable conflict responses.
- [ ] Relevant backend tests pass on the current branch.
- [ ] `flutter analyze` and `flutter test` pass on the current branch.
- [ ] Tenant-isolation and version-conflict cases have test evidence.
- [ ] No unrelated local work is lost and no unapproved merge/release occurs.

## Evidence log
Add date, branch/commit, exact command, result, and any failure/blocker after running the checks. Do not mark this phase `Tested` based on old logs or test files alone.

| Date | Branch/commit | Command/check | Actual result |
|---|---|---|---|
| — | — | Evidence not recorded during documentation reorganization | — |

## Next action
Run the relevant backend integration tests and Flutter checks against the latest local branch; review any failures before accepting Phase 0.
