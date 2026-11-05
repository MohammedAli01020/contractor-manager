# Pull Request Cleanup — November 2026

**Review baseline:** `integration/stabilization-2026-11`  
**Review date:** 2026-11-05

## Policy

- The integration branch is the current working source of truth for active product development.
- Historical PRs are not merged merely to preserve their history.
- Close a PR only after verifying that its useful changes are already integrated, deliberately ported, or explicitly superseded.
- Keep independent workstreams open when their content is still needed, but do not merge stale branches wholesale.
- No PR merge, no `main` modification, and no production migration is authorized by this cleanup.

## Reviewed PRs

| PR | Subject | Status after review | Decision |
|---|---|---|---|
| #40 | WF-06 hourly compensation | Closed | Superseded by the current integration line; no merge |
| #29 | Completed-shift conflict messaging | Closed | Fix was reapplied to the current integration line; no merge |
| #27 | Auth/session tests (rebased) | Closed | Focused files were verified previously to match integrated versions; no merge |
| #26 | Older auth/session test PR | Closed | Superseded by #27; no merge |
| #13 | Earlier AuthBloc/session tests | Closed | Superseded by the later auth work; no merge |
| #30 | Legacy completed-shift correction design | Closed | Historical design reference; current correction work proceeds through WF-08A/B/C |
| #15 | Accounting mobile screens + offline-first docs | Open — port/reconcile required | Stale base (30 commits ahead, 268 behind integration); retain useful content, do not merge as-is |
| #32 | Flutter completed-shift correction UI | Open — reference only | Stale base (16 ahead, 159 behind); reconcile with current WF-08 API and compensation contract before reuse |
| #33 | Design System v1.0 | Open — separate design stream | Independent docs on main (15 changed files); review and port deliberately, not into main automatically |

## Remaining actions

1. Reconcile PR #15's accounting screens, pagination test, and offline-first/product-roadmap documentation against the integration branch. Port only missing, approved work in a dedicated branch.
2. Treat PR #32 as UX reference for WF-08C. Rebuild/reconcile against the finalized backend correction contract; do not copy its stale request payload blindly.
3. Review PR #33's design documents as a separate product-design workstream. Keep it independent from the correction implementation.
4. Complete WF-08A snapshot semantics and CI before starting WF-08B.
5. Revisit the three remaining open PRs after their useful content has been ported or explicitly scheduled.

## Safety notes

- All closures above are closures of obsolete review paths, not merges.
- No change to `main` was made.
- No production database migration was run.
- CI status for WF-08A remains unconfirmed until an actual workflow run is observed.
