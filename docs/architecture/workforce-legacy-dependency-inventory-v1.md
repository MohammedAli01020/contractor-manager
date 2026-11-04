# Workforce migration: legacy driver dependency inventory

**Review date:** 2026-11-04  
**Repository:** `MohammedAli01020/contractor-manager`  
**Reviewed source branch:** `feature/financial-foundation`  
**Reviewed source commit:** `e03e49b1fbe882e08dde702acb7a9ff3fcd05c84`

This is a source-level architecture inventory. The recursive Git tree was complete (384 blob paths, not truncated). It is not a runtime database inspection and makes no claim about deployed tenant row counts or data state.

## 1. Current domain and API shape

- `Driver` maps to tenant-scoped table `drivers`. It currently combines person identity (name, phone, national ID), a two-value `WorkerType` (`DRIVER` / `WORKER`), operational availability, active status, notes, and morning/evening/night shift rates.
- `User` is a separate tenant-scoped authentication entity with username, password, role, active and login metadata. A workforce member must not automatically become a system login.
- Driver CRUD is exposed under `/api/v1/drivers`; statement, advances and payments use `/api/v1/drivers/{driverId}/ledger`.
- Shift APIs accept/return `driverId`; list filters, defaults, running shifts, projections, audit history, correction flow, and mobile editor depend on it.
- `LiveStatusService` groups running shifts by `driverId`, joins active Driver records and calculates availability. Flutter's driver screen and shift editor consume these driver-shaped payloads.
- Flutter currently exposes only Driver/Worker and three shift rates. Shift creation requires a driver selection and sends `driverId`; shift cards and bulk finish display the driver name.

## 2. Database constraints and triggers

| Migration | Dependency / invariant |
|---|---|
| V3 | Creates `drivers`, tenant-scoped unique `(id, tenant_id)`. |
| V4 | Creates `shifts.driver_id NOT NULL`, composite FK to `drivers(id, tenant_id)`, and snapshots `driver_wage`. |
| V6 | Generated active-driver key + tenant unique constraint: a driver cannot have multiple `IN_PROGRESS` shifts. |
| V7/V8 | MySQL triggers prevent a driver becoming unavailable while an active shift exists. |
| V17 | `journal_lines.driver_id` has a tenant-safe FK to `drivers`; journal posting validates the tenant relationship. |
| V19 | Links a shift to its one-time wage journal with a tenant-safe FK. |
| V20 | Creates `driver_ledger_entries` with tenant-safe FKs to driver, shift and journal; request-key/journal uniqueness; statement index by tenant/driver/date/id. |
| V22 | Adds composite shift/tenant/driver relationship, one-original-earning-per-shift uniqueness, and append-only ledger triggers. |
| V27/V28 | Enforce positive wage and matching wage journal when a shift becomes completed. |
| V29/V32 | Replace the earlier completed-shift trigger with a correction-aware guard; V32 removes the superseded V22 trigger to avoid conflicting protections. |
| V34/V35 | Protect applied correction audit records and validate correction reversal/replacement ledger rows against the exact authorized journals. |
| V23/V25 | Posted journals/lines are immutable; financial idempotency keys are tenant-scoped. |

The current shift correction protocol deliberately keeps the driver identity unchanged: V29's trigger requires old and new driver IDs to match. `ShiftCorrectionService` is designed around that rule. A worker transfer/correction cannot be implemented by simply changing `driver_id`.

## 3. Financial source behavior

- `ShiftService.finish` recognizes the positive shift wage, creates the wage journal, links it to the shift, and inserts the earning into the driver ledger in one transaction. It serializes wage recognition against payments using a driver-row lock and READ_COMMITTED balance reads.
- `DriverLedgerService` locks the driver before payment/advance balance checks. It distinguishes an advance against earned payable from a recoverable advance and posts cash/bank journal lines with the driver dimension.
- `DriverLedgerEntryRepository` calculates statement and recoverable-advance balances by `tenantId + driverId`; these must be generalized without splitting the authoritative balance.
- `JournalLine`, `JournalLineRequest`, `JournalPostingService`, and journal idempotency comparison all carry and validate `driverId`. Journal dimensions are a distinct migration consumer from the payable ledger.
- System accounts include driver-named payable and wage expense codes 2100 and 5000, plus recoverable worker advances asset code 1300. Existing tenant chart rows and posted journal history must not be silently renamed/remapped.
- Expenses may reference shifts, but the reviewed expense schema has no direct driver dimension.

## 4. Mobile and test surface

- Driver UI: `mobile/lib/features/drivers/drivers_screen.dart`, `driver_edit_dialog.dart`.
- Shift data/API: `mobile/lib/features/shifts/data/models/shift_model.dart`, `data/datasources/shifts_api.dart`, `data/repositories/shifts_repository_impl.dart`, `presentation/bloc/shifts_bloc.dart`.
- Shift workflows: `presentation/shift_editor_screen.dart`, `bulk_finish_shifts_screen.dart`, `shifts_screen.dart`.
- Integration tests to extend: `DriverLedgerIdempotencyIntegrationTest`, `ShiftIntegrationTest`, `ShiftCorrectionIntegrationTest`, tenant-isolation tests, and accounting journal posting tests.
- MySQL integration tests are required for FK, trigger, idempotency, rollback, locking and tenant isolation behavior; unit tests alone are insufficient.

## 5. Migration consequences

1. Add tenant-scoped `workforce_members` and a unique, validated link from each legacy driver to exactly one workforce member. Backfill without changing driver IDs.
2. First migration remains additive. Do not drop or make nullable `shifts.driver_id`, `driver_ledger_entries.driver_id`, or `journal_lines.driver_id` while legacy workflows depend on them.
3. Add a tenant-safe workforce dimension to journal lines and workforce-aware statement/service only after mapping is validated. Decide explicitly how new generic events relate to legacy driver references; never create two authoritative balances.
4. Preserve active-driver uniqueness for the current shift workflow. Do not replace it with a global one-active-shift-per-person rule until assignments/operational profiles define which roles consume the same slot.
5. Preserve shift wage snapshots and posted-journal references. Effective-dated compensation agreements must not rewrite historic rates or posted entries.
6. Extend the audited correction envelope before allowing a shift's worker identity to change. Until then, retain same-driver correction behavior.
7. Keep legacy driver APIs and Flutter payloads working; add generic workforce read/write paths alongside them and deprecate only after reconciliation evidence.
8. Before implementation, produce a table-by-table migration specification: preflight queries, backfill rules, FK/index/trigger replacement order, rollback boundaries, API compatibility matrix and acceptance tests.

## 6. Review status

- Core Driver, Shift, ledger, journal, User, live-status, audit, expense, API/DTO, repository, Flutter driver/shift, migration and integration-test surfaces were reviewed.
- Re-run the inventory against the latest `feature/financial-foundation` head immediately before implementation.
- No application code, migration, deployed database, PR, or `main` branch was changed by this review.
- The approved architecture authorizes detailed technical planning only. Implementation and PR merges remain subject to explicit user approval.
