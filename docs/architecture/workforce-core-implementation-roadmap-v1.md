# Workforce Core implementation work breakdown

**Status:** PR-WF-01 was merged into `feature/financial-foundation` on 2026-11-04 as squash commit `dc181ebb8de55edf866943cf7b69e173b0dd90b6`. PR-WF-02 implementation is complete on `feature/workforce-core-pr-wf-02` and PR #35 is ready for review; Backend CI runs 524 and 536 passed. PR-WF-03 implementation is complete on `feature/workforce-core-pr-wf-03` and stacked PR #36 is ready for review; Backend CI run 535 passed (229 tests, no failures/errors/skips). PR-WF-02 and PR-WF-03 remain unmerged. No production migration or `main` change was made.  
**Date:** 2026-11-04  
**Parent branch for future work:** `feature/financial-foundation` (revalidate before starting)  
**Design package:** commercial ERP foundation, legacy dependency inventory, additive migration proposal, compensation/HR design, [PR-WF-01 migration specification](workforce-pr-wf-01-migration-spec-v1.md), and [PR-WF-04 compensation agreements specification](workforce-pr-wf-04-compensation-agreements-spec-v1.md).

## 1. Delivery rules

- Work on a dedicated feature branch from the then-current approved financial foundation branch.
- Never merge a PR or modify `main` without explicit product-owner approval.
- Each PR must be small, independently reviewable, and include its own tests and documentation.
- No destructive rename/drop of legacy driver tables or columns in the Workforce Core release.
- Do not introduce a second authoritative payable balance.
- Keep existing Flutter shift and driver workflows backward-compatible until the replacement screens are ready and verified.
- Database migrations are forward-only in production; define repair/rollback strategy before each migration.
- Re-run the full source inventory against the actual branch head before implementation; the source commit in the current docs is a planning baseline, not a guarantee of today's head.

## 2. Proposed PR sequence

### PR-WF-01 — schema and service foundation for WorkforceMember

Implementation branch: `feature/workforce-core-pr-wf-01` — [merged PR #34](https://github.com/MohammedAli01020/contractor-manager/pull/34), squash commit `dc181ebb8de55edf866943cf7b69e173b0dd90b6`. Detailed migration, backfill, preflight, postflight, test, and repair specification: [workforce-pr-wf-01-migration-spec-v1.md](workforce-pr-wf-01-migration-spec-v1.md).

Scope:
- Add tenant-scoped `workforce_members`.
- Add nullable `drivers.workforce_member_id` with composite tenant FK and uniqueness.
- Backfill one member per existing driver, preserving driver IDs.
- Add the WorkforceMember entity/repository and a Driver-to-member mapping.
- Keep DriverService as the only write entry point; create/update/deactivate synchronizes the linked member in the same transaction.
- Add MySQL migration, tenant-isolation, uniqueness, and synchronization tests.
- No public API contract changes and no mobile changes.
- WorkforceMember becomes the canonical identity write owner only in PR-WF-02.

Gate:
- Every legacy driver maps to exactly one member.
- No cross-tenant links or duplicate mappings.
- Pre/post row counts and identity fields reconcile for every tenant.
- Existing driver/shift/ledger/journal tests pass on MySQL.

### PR-WF-02 — canonical identity compatibility

Specification: [workforce-pr-wf-02-canonical-identity-spec-v1.md](workforce-pr-wf-02-canonical-identity-spec-v1.md). Implementation branch: `feature/workforce-core-pr-wf-02` (created from `feature/financial-foundation` at `dc181ebb8de55edf866943cf7b69e173b0dd90b6`); [PR #35](https://github.com/MohammedAli01020/contractor-manager/pull/35) is ready for review and remains unmerged.

Scope:
- Make workforce identity the write owner for person-level name/contact/national ID/notes.
- Preserve legacy driver endpoints and response shape.
- Add a reconciliation report for any retained mirrored columns.
- Protect national ID from broad list responses and enforce permission checks on detail access.

Gate:
- Legacy API clients continue to work.
- No independent conflicting writes to identity fields.
- Audit and optimistic locking remain correct.

### PR-WF-03 — roles and assignments

Implementation branch: `feature/workforce-core-pr-wf-03`; [stacked PR #36](https://github.com/MohammedAli01020/contractor-manager/pull/36) is ready for review and remains unmerged. Backend CI run 535 passed: 229 tests, 0 failures, 0 errors, 0 skipped. The PR targets the PR-WF-02 branch and must be revalidated after PR-WF-02 is merged.

Scope:
- Add tenant role catalog with seeded common roles and tenant-defined roles.
- Add effective-dated `workforce_assignments`.
- Support multiple roles and optional department/project/site references.
- Add service-level overlap validation under workforce-member locking.

Gate:
- Multiple legitimate roles per person supported.
- Invalid date ranges and unauthorized tenant references rejected.
- Existing driver eligibility and active-shift constraints unchanged.

### PR-WF-04 — compensation agreements and legacy-rate conversion

Detailed implementation specification: [workforce-pr-wf-04-compensation-agreements-spec-v1.md](workforce-pr-wf-04-compensation-agreements-spec-v1.md). This is design only; no implementation has started. The design splits delivery into schema/domain (04A), agreement APIs (04B), and report-first legacy conversion tooling (04C). A per-tenant cutover business date must be explicitly approved before conversion.

Scope:
- Add effective-dated agreement header and typed components, bound to a workforce assignment.
- Represent SHIFT, HOUR, DAY, QUANTITY and FIXED; MIXED is multiple components. First rollout enables SHIFT only.
- Map legacy DRIVER/WORKER operational profiles to matching role assignments and three SHIFT components at an explicitly approved per-tenant cutover date.
- Keep legacy columns unchanged as monitored compatibility values; do not rewrite historical shifts.
- Add overlap validation, immutable approved agreements, idempotent conversion and pre/post reconciliation.
- Approval of an agreement has no journal/payable effect.

Gate:
- Correct component and assignment selected by shift type and business date.
- Zero-rate values are preserved; current zero-wage completion behavior is not changed in this PR.
- Preflight and postflight prove exact rate equality and no historic shift/journal/ledger changes.
- Conversion is idempotent; no overlapping ambiguous approved agreement for the same assignment/component scope.
- No production conversion without a separately approved per-tenant cutover date and reviewed report.

### PR-WF-05 — shift integration with agreement snapshots

Scope:
- Resolve current shift rate through the agreement service.
- Persist agreement/component/rate snapshot references on new shifts where appropriate.
- Preserve current shift completion journal and ledger transaction semantics.
- Keep legacy API/mobile payloads stable.

Gate:
- New shift wage snapshot deterministic and immutable after posting.
- Old shifts remain byte-for-byte equivalent in financial values.
- Finish/payment concurrency and idempotency tests pass.

### PR-WF-06 — workforce payable and journal dimensions

**Approved design decision (2026-11-04):** preserve the physical table name `driver_ledger_entries` during the compatibility transition. Add `workforce_member_id` and backfill it through the existing `drivers.workforce_member_id` mapping. Keep `driver_id` and all historical references. The preferred eventual name is `workforce_ledger_entries`, but a physical rename is deferred until dependency removal is proven; retaining the old name permanently is acceptable if the rename has no material benefit.

Scope:
- Add `workforce_member_id` to existing `driver_ledger_entries` and journal lines in a controlled, separately reviewed compatibility migration.
- Backfill the workforce dimension from the existing driver mapping.
- Generalize statement/read services to workforce identity without dual ledger writes.
- Retain `driver_id` and all legacy shift/journal references while driver-originated events still depend on them.
- Do not yet enable generic non-driver payroll posting until reconciliation and constraint changes are separately approved.
- Keep the physical table name `driver_ledger_entries` during compatibility. A rename to `workforce_ledger_entries` is optional and must not happen until all legacy code, reports, integrations, triggers, correction paths, and tooling no longer depend on the old name. The recommended default is to keep the physical name if renaming provides no material benefit.

Gate:
- Every historical ledger row maps to one workforce member.
- Worker statements and all financial balances reconcile before/after.
- Journal reversals preserve dimensions.
- Tenant-safe FK, trigger, idempotency, rollback and lock tests pass.

### PR-WF-07 — generic earning sources and draft payroll

Scope:
- Add approved time/quantity/fixed-period source records as required by first supported use cases.
- Add payroll period/run as calculation and approval workflow only.
- Include existing shift earnings as already recognized.
- No posting from draft or recalculation.

Gate:
- Each earning line traces to a source, agreement component, rate version and calculation inputs.
- Draft/recalculation has no accounting effect.
- Shift earning is never double-posted.

### PR-WF-08 — approval and posting of non-shift earnings

Scope:
- Add approval workflow and idempotent journal/payable posting for approved hourly/day/quantity/fixed earnings.
- Keep actual payment through existing cash/bank settlement controls.
- Add reversal/replacement workflow for corrections.

Gate:
- One source/component posts once under retries and concurrency.
- Worker payable statement and GL reconcile.
- Partial payments, advances, recoveries and deductions reconcile.

### PR-WF-09 — Flutter Workforce Core screens

Scope:
- Add workforce list/detail/create/edit.
- Add role/assignment and compensation agreement views.
- Keep driver operational controls and shift selection behavior intact.
- Add permission-aware sensitive field display.

Gate:
- Real Android device workflow verified.
- No regression to current shift creation, completion, correction or payment screens.
- Empty/loading/error/offline states handled.

## 3. Release boundaries

- Workforce identity + legacy mapping can ship without payroll.
- Roles/assignments can ship without attendance.
- Compensation agreements can ship first for shift-based drivers while legacy fallback remains.
- General non-shift payroll is a later feature, not a hidden side effect of adding workforce identity.
- Full attendance, leave, overtime, statutory tax/insurance localization, employee self-service and advanced HR remain later releases.

## 4. Required release evidence

For every PR:
- migration preflight and postflight output
- tenant-isolation test results
- MySQL integration test results for constraints/triggers/locking
- financial reconciliation report where ledger/journal is touched
- API compatibility test evidence
- Flutter real-device verification where mobile changes
- updated architecture/operations documentation

## 5. Approval boundary

This work breakdown is a proposed execution order. PR-WF-01 was explicitly authorized and merged only into `feature/financial-foundation`; `main` was not changed. PR-WF-02 has been authorized for implementation on its dedicated branch, but must remain unmerged until review and explicit merge approval. Every later PR requires its own explicit approval.
