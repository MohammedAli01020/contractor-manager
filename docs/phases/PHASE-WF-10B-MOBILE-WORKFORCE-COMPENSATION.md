# WF-10B — Mobile Workforce and Compensation Agreements

## Status

Implementation branch: `feature/wf10b-mobile-compensation-agreements`  
Base: `feature/wf10a-workforce-identity-api`  
PR: pending CI and review.  
No merge, production migration, or `main` change is authorized by this work.

## Delivered in this slice

- Adds a mobile entry point for the canonical Workforce directory.
- Reads the tenant-scoped `GET /api/v1/workforce/members` page and uses `workforceMemberId` as the identity for all agreement operations. The legacy `driverId` remains a separate operational identifier.
- Shows active/inactive state and whether an operational driver profile is linked.
- Adds per-member compensation agreement history.
- Allows ADMIN users to create and approve a manual hourly agreement in EGP.
- Supports replacement of an existing approved agreement using the server's dedicated atomic `approve-replacement` endpoint, preserving assignment scope and requiring a change reason.
- Allows ADMIN users to approve or cancel existing drafts. Non-admin users can inspect the directory and agreement history but do not see write actions.
- Adds Flutter CI branch coverage for this feature.

## Hourly agreement workflow

1. Create/select the person through the existing Drivers & Workers screen.
2. Open **القوى العاملة والأجور** from the mobile home screen.
3. Select the canonical workforce member.
4. Create an hourly agreement with its rate and effective-from date.
5. Review and approve it. If replacing an approved agreement, provide a reason; the server ends the previous agreement the day before the new start and approves the replacement in one transaction.
6. Start a shift for that worker on/after the effective date. The shift defaults should resolve the approved agreement and display the frozen hourly rate.
7. Finish the shift by entering actual hours in the existing measurement dialog. The server remains authoritative for validation and final wage calculation.

## Explicit boundaries

- This screen currently creates HOUR agreements only. SHIFT legacy rates remain available through the existing worker profile and shift flow.
- DAY and FIXED are not exposed because the complete mobile/operational lifecycle is not yet validated.
- QUANTITY remains in its existing backend/mobile path and is not part of this agreement editor slice.
- This feature does not change historical shifts, journal entries, ledger balances, or compensation snapshots.
- A failed approval after draft creation leaves the draft visible; the screen reloads the agreement history rather than silently creating a duplicate.
- Backend tenant isolation, date overlap, assignment scope, approval, and replacement rules remain enforced by the API.
- No production database or Railway environment was accessed.
