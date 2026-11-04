# PR-WF-03 — tenant roles and effective-dated assignments

**Status:** Implemented on `feature/workforce-core-pr-wf-03`; Backend CI run 535 passed (229 tests, 0 failures, 0 errors, 0 skipped); ready for review in PR #36.  
**Depends on:** PR-WF-02 canonical identity branch.  
**No production migration or main-branch change is authorized.**

## 1. Goals

- Give every tenant a role catalogue with stable role codes and editable tenant-defined role records.
- Support multiple concurrent roles for one WorkforceMember.
- Preserve assignment history with inclusive effective dates.
- Keep operational eligibility separate from a workforce role label.

## 2. Role catalogue

Seed the following system roles for tenants existing at migration time and provision them transactionally for tenants created afterward:

- DRIVER
- EQUIPMENT_OPERATOR
- WORKER
- TECHNICIAN
- SUPERVISOR
- ENGINEER
- ADMINISTRATIVE

A tenant may create additional role codes. Codes are normalized to uppercase and unique within a tenant. System-defined roles cannot be deactivated. Custom role deactivation is soft; historical assignments remain intact and inactive roles cannot be assigned to new work.

Application login roles (ADMIN, OPERATOR, VIEWER) remain separate from workforce job roles. A job role never grants a login or security permission.

## 3. Assignment model

Each assignment has:
- tenant_id
- workforce_member_id
- workforce_role_id
- optional site_id
- effective_from (inclusive)
- effective_to (inclusive, nullable)
- lifecycle status (ACTIVE, ENDED, CANCELLED)
- notes, optimistic version and audit timestamps

Project and department references are intentionally omitted because the inspected branch has a tenant-scoped sites table but no verified project/department master-data model. They can be added later with tenant-safe composite FKs when those domains exist.

## 4. Overlap and concurrency

- A person may hold different roles over overlapping dates.
- The same person may not hold the same role over overlapping date ranges, including across different sites.
- Date overlap is inclusive: existing.start <= requested.end (or no requested end) and existing.end >= requested.start (or no existing end).
- The service locks the WorkforceMember row first, then validates overlap and writes the assignment. This serializes concurrent assignment creation for one person.
- Ending an assignment follows the same member-first lock order, locks the assignment after the member, and requires the current optimistic version.
- The database CHECK constraint rejects end dates before start dates. Tenant-safe composite FKs enforce member, role, and site ownership.

## 5. API and authorization

- GET /api/v1/workforce/roles — ADMIN, OPERATOR, VIEWER
- POST /api/v1/workforce/roles — ADMIN
- DELETE /api/v1/workforce/roles/{roleId} — ADMIN; soft deactivation only
- GET /api/v1/workforce/members/{memberId}/assignments — ADMIN, OPERATOR, VIEWER
- POST /api/v1/workforce/members/{memberId}/assignments — ADMIN
- PUT /api/v1/workforce/members/{memberId}/assignments/{assignmentId}/end — ADMIN

Every lookup is tenant-scoped. Cross-tenant member, role or site references fail closed. An inactive member, role or site cannot receive a new assignment.

## 6. Operational boundary

A DRIVER or EQUIPMENT_OPERATOR workforce role does not by itself make the person eligible for shift selection. Existing Driver operational profile, availability, worker type, active-shift uniqueness, equipment/site constraints and shift wage snapshots remain authoritative until a later operational-profile integration PR.

No shift, journal, payable ledger, compensation agreement, payroll, attendance, Flutter screen or login permission changes are part of PR-WF-03.

## 7. Migration

V37 creates workforce_roles and workforce_assignments, seeds the common role catalogue for tenants already in the database, and adds composite tenant-safe FKs to workforce_members, roles and sites. New tenant provisioning calls the same role definitions inside the tenant registration transaction.

The migration is additive. No production migration has been applied.

## 8. Acceptance tests

MySQL/Testcontainers integration tests cover:
- common role provisioning and tenant custom role creation/deactivation
- system role protection
- multiple different roles over overlapping dates
- rejection of same-role overlapping dates
- ending an assignment and allowing a subsequent non-overlapping assignment
- stale version rejection
- invalid date range rejection
- cross-tenant member, role and site rejection
- ADMIN write / OPERATOR and VIEWER read-only access

CI explicitly requires all six tests in WorkforceAssignmentIntegrationTest to execute with zero skips, failures or errors.
