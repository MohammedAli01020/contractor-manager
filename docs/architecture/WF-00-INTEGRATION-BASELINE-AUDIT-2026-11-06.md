# WF-00 — Integration baseline audit (2026-11-06)

## Scope and evidence

Audited `integration/stabilization-2026-11` at `a63e49b7a100f32af39d0cf5a20575cb512ba506` using the GitHub branch ref, recursive repository tree, and source files at that exact ref. This is a source-presence audit, not a fresh local build or runtime test.

The branch comparison confirms `feature/financial-foundation` at `6d32ef4fb24b524e6d7a6688ea0ea6a7592a4db8` is an ancestor of integration; integration is 90 commits ahead of that historical feature branch. Therefore WF-01 through WF-05 merged into the feature branch are included in the audited integration line.

## Verified baseline

| Area | Finding on integration |
|---|---|
| Canonical identity | Present: `workforce_members`, driver mapping via `workforce_member_id`, `WorkforceIdentityService`; legacy Driver remains the operational compatibility profile. |
| Roles and assignments | Present: tenant roles, dated assignments, overlap validation/locking, tenant-scoped APIs. Job roles remain separate from application login roles. |
| Compensation agreements | Present: draft, approval, replacement approval, cancellation, components and effective-date resolution. |
| Shift wage snapshot | Present: `ShiftCompensationResolver` resolves the effective agreement and freezes the component/rate/unit on shift. Legacy driver-rate fallback remains for members without approved agreements. |
| SHIFT / HOUR / QUANTITY | Present in the shift lifecycle. HOUR requires explicit actual hours; QUANTITY requires explicit actual quantity. Backend computes the final wage; mobile supports measurement entry/preview and excludes measured shifts from bulk finish. |
| Worker ledger | Present under legacy Driver route: `/api/v1/drivers/{driverId}/ledger`, payments and advances. Advance types include `AGAINST_EARNED` and `RECOVERABLE`; journal/idempotency/locking code is present. |
| Workforce mobile management | No dedicated Workforce feature/screens found in the audited mobile tree; the app still has legacy Drivers screens. Backend APIs exist for roles, assignments and agreements. |
| Worker self-service/login | Not established by this source audit; do not treat as delivered. |
| DAY / FIXED compensation | Not confirmed as operationally resolved by the inspected shift resolver. Do not advertise these bases as usable until implementation and tests prove them. |

## Important compatibility and product boundaries

- `Driver` still owns operational shift selection and the current worker ledger route; WorkforceMember is the shared identity layer. Future work should migrate APIs/UI toward workforce identity without breaking legacy driver IDs or ledger history.
- Workforce job roles do not grant application permissions.
- Approved compensation agreements are resolved by shift business date and their values are frozen on the shift. Later agreement changes must not reprice historical shifts.
- Worker advances remain separate from generic expenses.
- Existing ledger and accounting foundations do not by themselves prove a complete worker-facing app or payroll product.

## WF-08 correction gate — not part of integration baseline

The audited integration SHA is the base of PR #41. The measured correction stack is still separate:

- PR #41 WF-08A: base `integration/stabilization-2026-11`, head `2581a1c1ff09e6ef528d28648d341d7bb3f2e435`, open and unmerged.
- PR #42 WF-08B: stacked on WF-08A, head `3b090e4b3eafdc1d0db790c56c4a3e76ed404468`, open draft and unmerged.
- PR #43 WF-08C: stacked on WF-08B, head `c9ab87eaa3f8447b78417c9f13ff3af1f9595f6b`, open draft and unmerged.

A compare of integration against the WF-08C head shows 64 commits ahead of integration, with the WF-08A/B backend migrations V47–V51 and WF-08C mobile measured correction files in that stack. The integration version of `ShiftCorrectionService` still explicitly rejects HOUR and QUANTITY corrections; its Flutter correction screen remains the SHIFT-only form. Therefore testing the integration branch alone will not exercise WF-08C.

## Required real-device acceptance

Before calling WF-08C complete, run the app from the exact WF-08C branch/head with its stacked WF-08A/B backend and test on the actual Android device:

1. HOUR correction: show frozen hourly rate and old hours; accept valid corrected hours only; verify no client wage is sent.
2. QUANTITY correction: show frozen rate/unit and old quantity; accept valid corrected quantity only; verify no client wage is sent.
3. SHIFT regression: legacy correction flow remains usable.
4. Confirm reason, confirmation, error display, retry/idempotency behavior, updated shift details and correction history.
5. Verify old journal/earning reversal and replacement effects through the test environment; do not use production data.

Record pass/fail and screenshots or notes. Until this is complete, WF-08C is code/CI-complete only to the extent stated in PR #43; it is not device-accepted, and WF-08D/release approval remains blocked.

## Guardrails

No PR was merged, no branch was retargeted, no production migration was run, and `main` was not changed during this audit.
