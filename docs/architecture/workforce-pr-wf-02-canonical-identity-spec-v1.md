# PR-WF-02 — Canonical workforce identity compatibility specification

**Status:** Approved to implement as a separate feature branch; branch `feature/workforce-core-pr-wf-02` created from `feature/financial-foundation`. No implementation code has been committed yet.
**Date:** 2026-11-04
**Base commit:** `dc181ebb8de55edf866943cf7b69e173b0dd90b6`
**Parent:** PR-WF-01 merged into `feature/financial-foundation`; no production migration applied and `main` unchanged.

## 1. Goal

Make `WorkforceMember` the canonical owner of person-level identity fields while retaining the legacy Driver API and operational model. A single transactional application write path must update the canonical identity and its legacy compatibility mirror. There must be no independent writers and no breaking change to current Flutter payloads.

Person-level fields in scope:
- full name
- phone
- national ID
- notes
- active lifecycle state

Driver-only operational fields remain on `Driver`: worker type, availability/reason, shift wage rates during the legacy-rate transition, and the legacy driver identifier. Shift selection and shift-to-driver references remain unchanged.

## 2. Verified baseline observations

Reviewed on `feature/financial-foundation` at `dc181ebb8de55edf866943cf7b69e173b0dd90b6`:
- `DriverService` currently creates `WorkforceMember` and mirrors identity fields from Driver. On update/deactivation, Driver remains the write owner and copies its fields to WorkforceMember.
- `DriverResponse` currently reads all identity fields directly from Driver.
- `DriverController` exposes list and detail under ADMIN/OPERATOR/VIEWER; create/update/deactivate are ADMIN-only.
- `DriverResponse` currently includes `nationalId` in both list and detail responses, so broad list access exposes a sensitive identifier.
- `Driver` remains referenced by shift operations and live-status display; legacy operational reads must continue working.
- `WorkforceMember` extends `BaseTenantEntity` and has its own optimistic version. Its version may legitimately differ from Driver's because rate/availability-only Driver updates do not change identity. Do not compare the two version values or copy one version over the other.
- Driver update currently locks the Driver row. The compatibility write path must use a documented, consistent lock order (Driver then WorkforceMember) and must not introduce another reverse-order writer in this PR.

## 3. Required implementation behavior

### 3.1 Canonical write ownership
- Introduce a focused workforce identity application service (or equivalent single service boundary) that owns writes to person-level identity.
- Legacy Driver create/update/deactivate operations delegate person identity changes through that boundary within the existing transaction.
- On create, create the WorkforceMember identity and then create its Driver operational profile with the mapping.
- On update, load/lock the linked WorkforceMember using both member ID and tenant ID, validate its existence, update canonical identity, and synchronize the legacy Driver columns as compatibility mirrors in the same transaction.
- On deactivate, update canonical active state and the legacy mirror atomically.
- Rate/availability-only changes must not update WorkforceMember timestamps/version unnecessarily.
- A missing, cross-tenant, or inconsistent mapping must fail closed; never silently recreate a member or detach a driver.
- Do not expose an independent public WorkforceMember write endpoint in this PR. This avoids adding a second path before roles/assignments and API authorization are designed.

### 3.2 Read ownership and compatibility
- Legacy Driver list/detail response shape and field names remain stable.
- Person-level response values are sourced from WorkforceMember, not from the mirrored Driver columns.
- Driver operational fields remain sourced from Driver.
- The Driver mirror remains transactionally synchronized because shift/live-status and other legacy operations still use Driver.
- Add batch tenant-scoped member lookup for list responses; avoid an N+1 query per driver.
- Missing member mapping during a read must be treated as an integrity error and surfaced safely, not silently fall back to stale Driver identity.

### 3.3 National ID protection
- Keep the `nationalId` JSON property in the legacy DTO for compatibility.
- For list responses, always return `nationalId: null`; never include the raw identifier in broad directory/list results.
- For detail responses, reveal national ID only to ADMIN. For OPERATOR and VIEWER, return the same property as null.
- Do not add national ID to audit text, logs, search indexes, or exception messages.
- Add explicit authorization/serialization tests for ADMIN, OPERATOR, and VIEWER. Tenant scoping remains mandatory regardless of role.
- This is field-level redaction, not a new endpoint or a change to the route contract.

## 4. Concurrency and transaction rules

- Preserve the existing Driver pessimistic lock for legacy updates.
- Lock the linked WorkforceMember after Driver (Driver -> WorkforceMember); use tenant-scoped lookup/lock.
- Do not compare Driver.version with WorkforceMember.version; they are separate optimistic-lock domains.
- Ensure all person identity updates occur in the same transaction as the compatibility mirror update and audit event.
- Concurrent requests must not produce canonical/mirror divergence. Test competing identity updates and stale Driver versions.
- No shift, journal, payable ledger, account code, wage snapshot, or financial posting changes.

## 5. Test and acceptance plan

Required:
1. Driver creation creates exactly one WorkforceMember with matching identity.
2. Updating identity through the legacy Driver endpoint updates WorkforceMember and the Driver mirror atomically.
3. A failure during either write rolls back both copies.
4. Rate/availability-only Driver changes do not touch WorkforceMember version or updated_at.
5. Driver list returns the unchanged JSON shape but redacts nationalId for all roles.
6. Driver detail returns nationalId for ADMIN and null for OPERATOR/VIEWER.
7. Cross-tenant member lookup is rejected; no member can be read or written outside CurrentUser.tenantId.
8. Missing mapping fails closed.
9. Existing shift creation/completion/correction, live-status, driver selection, and financial integration tests remain green.
10. MySQL integration coverage validates transactional behavior and tenant boundaries; do not rely on H2-only coverage.
11. Verify query count/batch loading to prevent N+1 list regressions.
12. Verify no national ID is written to logs/audit output.

## 6. Explicit non-goals

- No new general WorkforceMember CRUD API.
- No role catalog or WorkforceAssignment schema (PR-WF-03).
- No compensation agreement or shift-rate conversion (PR-WF-04/05).
- No ledger/journal dimension changes (PR-WF-06).
- No payroll, attendance, leave, or login-account linking.
- No Flutter UI changes unless a verified contract issue requires a separately approved change.
- No migration that changes historical financial data or driver IDs.
- No production deployment or merge to `main`.

## 7. Delivery and review gate

- Implement only on `feature/workforce-core-pr-wf-02`, based on the verified financial branch head.
- Run focused workforce/driver authorization tests, then the required backend suite and MySQL integration guards.
- Open a draft PR targeting `feature/financial-foundation`.
- Do not merge the PR until the product owner reviews and explicitly approves it.
- Update this specification with actual changed files, CI run, and final commit after implementation.
