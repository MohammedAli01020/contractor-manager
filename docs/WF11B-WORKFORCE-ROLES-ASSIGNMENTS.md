# WF11B — Workforce Roles & Assignments Mobile Completion

**Status:** Scope approved for implementation planning; implementation not included in this document  
**Base:** `feature/wf11a-workforce-member-creation`  
**Base commit audited:** `700bfd1baa36c22dec7ada6360e6e27bb4d11f14`  
**Delivery branch:** `feature/wf11b-workforce-roles-assignments`

## 1. Problem confirmed in WF11A

The canonical identity flow is present, but it currently creates and edits only common person fields:

- `POST /api/v1/workforce/members` and `PUT /api/v1/workforce/members/{memberId}` accept name, phone, national ID, notes, and version.
- The Flutter identity dialog submits only those fields; it has no role/classification control.
- Role and assignment endpoints already exist: `GET /roles`, `GET /members/{id}/assignments`, `POST /members/{id}/assignments`, and `PUT /members/{id}/assignments/{assignmentId}/end`.
- The directory projection currently derives `workerType` from a legacy `Driver` join. A new canonical member without a legacy Driver therefore has no category and is displayed as "تصنيف غير محدد".
- The current assignment response exposes role IDs, but not role names; the directory does not expose canonical role assignments.

This is an integration and projection gap, not a reason to create separate driver/worker identity records.

## 2. Product and domain decisions

1. **One person, one canonical Workforce Member.** A person may have multiple roles/capabilities and multiple time-bounded assignments.
2. **A new person must receive at least one initial role assignment before the add flow reports success.** Do not silently create a new unclassified person. If the role assignment fails, the complete create operation must roll back, or the UI must clearly preserve and resume an explicitly incomplete draft without claiming success. Preferred implementation is one backend transaction.
3. **Identity and assignment remain separate concepts.** Editing a person's name/phone/national ID must not rewrite or end assignments. Adding or ending a role is an assignment operation, not an identity edit.
4. **Roles are API-driven.** Flutter must load active tenant roles from `GET /roles`; do not hardcode role IDs or infer category from the old `workerType`.
5. **Multiple roles are supported.** Do not limit a person to one category. Prevent duplicate active overlapping assignments for the same person and role using the existing server-side rule.
6. **Assignment dates are explicit.** Start date is required; end date is optional. Validate date order on the server. Site association remains optional in this phase.
7. **Legacy compatibility is preserved.** Keep `drivers`, `driver_id`, existing driver operations, and historical financial references. Do not rename/drop tables or migrate legacy people as part of WF11B.
8. **Historical records are immutable.** Ending an assignment must not delete the assignment or alter historical shifts, approved compensation agreements, or posted journal entries.
9. **No DAY/FIXED compensation expansion.** This phase does not add payroll or new compensation bases. Existing SHIFT/HOUR/QUANTITY agreements remain governed by their existing lifecycle and must reference the correct assignment where applicable.
10. **Tenant isolation and authorization remain server-enforced.** Management actions are ADMIN-only; read access follows the existing Workforce policy.

## 3. Scope

### Backend

- Add an application-level transactional create flow that creates the canonical identity and its one-or-more initial role assignments atomically, while preserving the existing identity-only API contract for compatibility where required by existing callers. Prefer an explicit request shape or versioned application command; do not put orchestration logic in the controller.
- Validate every submitted role belongs to the current tenant and is active; validate optional site belongs to the same tenant and is active.
- Reuse the existing overlap protection and member locking. A failed initial assignment must not leave a successful-looking unclassified creation.
- Extend the directory response with canonical active role/assignment summaries (role ID, code, display name, assignment ID, dates, status, and optional site ID as appropriate). Do not return national ID or notes in the directory.
- Build the directory projection without multiplying person rows when a member has multiple assignments. Avoid per-row N+1 queries; use a bounded/batched projection or a suitable aggregate query.
- Preserve legacy `workerType` only as a clearly marked compatibility field if existing screens still depend on it. It must not be the source of truth for the Workforce category label.
- Ensure assignment list responses expose enough role display data for the mobile UI, either by enriching the response or by resolving role data in a bounded server-side query.
- Keep role creation/deactivation endpoints intact. A full custom-role administration UI is not required for this phase unless the existing role catalog proves unusable.

### Flutter mobile

- Replace the identity-only add dialog with a guided add flow:
  1. Common identity details.
  2. Select one or more active roles from the API.
  3. Set each initial assignment's effective-from date; allow optional site and notes.
  4. Submit and show success only after the backend confirms the complete operation.
- For edit, keep identity fields separate from assignment management. Editing identity must load the canonical detail first and preserve national ID when the current user's role is allowed to see it.
- Add a member details view/section showing identity, active roles, assignment history, effective dates, optional site, and status.
- Allow ADMIN to add a role assignment and end an active assignment with a required effective-to date. Do not physically delete assignments.
- Show role names/badges in the directory. Show a legacy/unclassified warning only for pre-existing records that genuinely lack a role; never show that as a normal successful result for a newly created person.
- Keep the screen mobile-first: short forms, clear loading/error/empty states, safe date pickers, and no dense desktop-style tables.

### Explicitly out of scope

- Renaming or dropping `drivers`, `driver_ledger_entries`, or other legacy tables.
- Deleting or bulk-merging existing people/driver records.
- National ID exposure in directory/search or to non-ADMIN users.
- Worker self-service/login provisioning and permission redesign.
- Advances, payroll runs, monthly close, or new financial posting behavior.
- DAY/FIXED compensation support.
- Offline writes or offline financial actions.
- Platform-owner web people administration.

## 4. API contract direction

The implementation should preserve existing endpoints and use a transaction-safe create orchestration. Exact JSON naming is to be finalized against the existing DTO conventions before coding.

Illustrative intent (not a frozen wire contract):

```json
{
  "member": {
    "fullName": "اسم الشخص",
    "phone": "اختياري",
    "nationalId": "اختياري",
    "notes": "اختياري"
  },
  "initialAssignments": [
    {
      "roleId": 1,
      "effectiveFrom": "2026-11-07",
      "siteId": null,
      "notes": null
    }
  ]
}
```

The server must reject an empty initial assignment list for the new guided create flow, reject duplicate/overlapping same-role assignments, and return the created member plus the canonical assignment summaries. Existing identity update remains separate and uses optimistic-lock `version`.

## 5. Required automated tests

### Backend integration tests (MySQL-backed)

- ADMIN creates a person with one initial role; identity and assignment are both persisted.
- ADMIN creates a person with multiple distinct roles; exactly one canonical identity exists and the directory returns one person with multiple role summaries.
- Empty initial role selection is rejected and leaves no orphan Workforce Member.
- Unknown, inactive, or cross-tenant role IDs are rejected.
- Cross-tenant or inactive site IDs are rejected.
- Invalid date range is rejected.
- Duplicate/overlapping same-person/same-role assignments are rejected; non-overlapping historical assignments remain valid.
- Ending an assignment requires ADMIN, correct member/assignment ownership, a valid end date, and the current version.
- Ending an assignment preserves the row and does not alter historical shift or financial snapshots.
- Directory includes canonical role names but excludes national ID and notes; non-ADMIN detail continues to redact national ID.
- Legacy Driver-linked identity synchronization and legacy driver operations continue to pass.
- Concurrent create/assignment requests do not create duplicate overlapping assignments or duplicate canonical identities.

### Flutter tests (Flutter 3.3.3 / Dart 2.18.2)

- Role list loads from the API; loading, empty, and failure states are actionable.
- Add form requires at least one role and submits the selected role IDs and effective dates.
- Multiple role selections are retained and submitted correctly.
- Failed assignment creation does not show a success message or leave an unnoticed unclassified record.
- Directory renders role labels from canonical role summaries, not from legacy `workerType`.
- Identity edit does not mutate assignments and preserves the loaded national ID.
- Assignment end flow sends the selected date and version; cancel does not mutate state.
- ADMIN-only controls are hidden/disabled for non-admin users while backend authorization remains tested.
- Existing Workforce compensation-agreement navigation and SHIFT/HOUR/QUANTITY flows remain green.

## 6. Acceptance on the real Android device

Do not call WF11B complete until the following are verified on the user's Android device against the isolated WF06 test environment:

1. Add an engineer with one role; it appears in the directory with the correct role label.
2. Add a driver/operator with two roles; one person appears once with both roles.
3. Edit the person's common identity fields; assignments and national ID remain intact.
4. Add another role with a later effective date; both assignment records remain visible with correct dates.
5. End an assignment; it moves to history and does not erase the person or prior shift history.
6. Open compensation agreements and verify existing agreement behavior remains intact.
7. Confirm a non-admin account cannot create/edit identities or assignments.
8. Confirm no duplicate identity is created through the legacy Drivers/Workers entry points.

## 7. Delivery and safety gates

- Stack implementation on `feature/wf11a-workforce-member-creation`; do not retarget or merge existing PRs as part of this work.
- No changes to `main`, no PR merge, and no production database operation.
- Prefer no database migration. If implementation proves a schema change is required, stop and document the migration, backfill, rollback, and compatibility impact before adding it.
- Run backend Maven tests with MySQL integration guards, Flutter tests and `flutter analyze` using Flutter 3.3.3, then GitHub Actions.
- Record exact commit SHA and CI run links. Keep the PR draft until automated CI and real-device acceptance are complete.
- Preserve all untracked local files and existing Docker volumes; never use destructive cleanup commands.

## 8. Current baseline validation

The WF11A base commit `700bfd1baa36c22dec7ada6360e6e27bb4d11f14` has successful GitHub Actions runs:

- Backend CI: run `42403625701`
- Flutter CI: run `42403625706`

These runs validate the WF11A baseline only; they do not validate WF11B.
