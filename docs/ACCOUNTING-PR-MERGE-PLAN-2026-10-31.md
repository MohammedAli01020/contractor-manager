# Accounting pull request integration plan — 2026-10-31

## Safety rules
- This is a planning checkpoint only. No pull request was merged and `main` was not modified.
- Do not run Flyway migrations against a deployed database as part of this review.
- Merge only after the relevant branch has been synchronized and the combined CI is green.

## Verified PR stacks

### Treasury, default chart, and trial balance
1. **PR #17** — `fix/treasury-concurrency-hardening` → `feature/driver-worker-ledger`
   - Head: `97daa6101450eae7e5c5591738c0d0b37fbb63da`
   - PR CI passed: run `41226085164`.
   - Parent branch run `41216492675` failed 6 of 160 tests (0 errors, 0 skipped). The failures were expense/shift flows receiving HTTP 409 because the cash/bank floor validation required opening liquidity balances in integration fixtures. PR #17 seeds balances in the affected fixtures and its PR stack passes.
   - Merge #17 into its own base only after owner approval; then rerun CI on the resulting `feature/driver-worker-ledger` head.

2. **Integration bridge required after #17**
   - `feature/driver-worker-ledger` is 80 commits ahead of `feature/financial-foundation` (merge base `dffcd689e5159ab7b254b7315b7b3b80d24c46ce`).
   - There is no reviewed PR yet to bring that completed ledger branch into `feature/financial-foundation`.
   - After #17 is integrated and the branch is green, open/review a dedicated integration PR from `feature/driver-worker-ledger` to `feature/financial-foundation`. Do not retarget #17 to the financial foundation because that would bundle the entire 80-commit feature branch into a narrow concurrency PR.

3. **PR #18** — `feature/accounting-default-chart-completion` → `fix/treasury-concurrency-hardening`
   - Head: `723f5c4500359d95c757f69f83b82dc61d3a26f6`
   - PR CI passed: run `41232227739`, including 169 tests and the MySQL current-read concurrency test.

4. **PR #19** — `feature/trial-balance-report` → `feature/accounting-default-chart-completion`
   - Head: `0a7a104e31165b430a30f5b526da2d07b41e57f2`
   - Refreshed PR CI passed: run `41232861265`, including MySQL accounting integration, V24 migration, default-chart/journal-linkage, and trial-balance integration guards.
   - At the latest comparison, the head was 9 commits ahead and 7 behind its base (merge base `f7356274d59c3bf42adbbe9b2c7defb60cf0a8d3`). Synchronize the branch with current #18 and rerun CI before merging.

### Accounting API and Flutter
5. **PR #14** — `feature/accounting-api-foundation` → `feature/financial-foundation`
   - Head: `95c474a64aad9c315a335ae5f9076c7aebd83f74`
   - CI passed: run `41232619556`.
   - Journal list is paginated (default 20, max 100), tenant-scoped, and ordered by accounting date, posted timestamp, and ID descending.

6. **PR #15** — `feature/accounting-mobile-foundation` → `feature/accounting-api-foundation`
   - Head: `f655e11d68f7431389d5850940e4555c6581e0d7`
   - Flutter CI passed: run `41232621672`.
   - The mobile list requests 20 items and loads additional pages on scroll.
   - At the latest comparison, the head was 20 commits ahead and 5 behind #14 (merge base `4883ebac4c3f310affdb98267673399d89d20928`). Synchronize with the current API branch and rerun Flutter CI before merging.
   - Add dedicated Flutter tests for pagination, duplicate prevention, and retry after a later-page failure before production readiness.

### Duplicate chart API
7. **PR #12** — `feature/chart-of-accounts-api` → `feature/financial-foundation`
   - This branch diverged from the base (6 ahead, 2 behind; merge base `8b19243c6555f0fb67e3215b76d948a915f81280`).
   - It exposes `/accounting/chart-of-accounts`, while #14 exposes the same chart resource through `/accounting/accounts`.
   - Do not merge both APIs as-is. Preserve/adapt #12's MySQL tenant-isolation integration coverage into the canonical #14 API tests, then close or repurpose #12 only with explicit maintainer approval.

### Independent Flutter auth tests
8. **PR #13** — `test/flutter-auth-bloc` → `feature/financial-foundation`
   - Latest recorded PR CI passed on `67c74eaea9cdc37035f18c0623cb95cbf67b0e43` (run `40848153455`).
   - Recheck after the base branch receives the accounting integration changes; keep independent from the accounting feature stack.

## Recommended order (not an authorization to merge)
1. Review and approve #17 into `feature/driver-worker-ledger`; rerun that branch's full CI.
2. Create and review the integration PR from `feature/driver-worker-ledger` into `feature/financial-foundation`; this is the required bridge for the 80-commit branch.
3. Keep #18 → #19 stacked on the treasury branch; synchronize #19 with #18 and rerun CI.
4. Keep #14 → #15 stacked on the financial/API branch; synchronize #15 with #14 and rerun Flutter CI.
5. Consolidate #12's tenant-isolation tests into the canonical #14 endpoint; do not ship duplicate chart APIs.
6. Revalidate #13 against the updated financial foundation.
7. Only after all integration PRs are green, review the complete financial foundation before any separate proposal to merge into `main`.

## Remaining quality gates
- Add Flutter tests for paged journal loading (next page, end-of-list, no duplicate rows, retry after error, refresh reset).
- Perform a real Android-device smoke test against the paginated API.
- Verify the merged API/mobile contract and tenant isolation with MySQL integration tests.
- Confirm production migration/back-up/rollback plan separately; CI migration tests do not authorize running migrations against production.


## Follow-up: Flutter pagination unit tests — 2026-10-31
- PR #15 latest head: `e0add33ffa0540304223ea584fed04147468824e`.
- Added a pure-Dart page parser and unique-ID append helper in `mobile/lib/features/accounting/journal_pagination.dart`.
- Added four tests covering page metadata, malformed response, duplicate IDs across pages, and empty next page.
- Flutter 3.3.3 analyze + tests passed in PR validation run `41233389375`; log reports 8 passing tests.
- Remaining: widget/integration-level retry and refresh tests plus real Android-device smoke test; branch synchronization with #14 is still required before merge.


## Comparison refresh after documentation checkpoints
The merge-plan and Flutter-test documentation commits were added directly to `feature/financial-foundation`. This moved that base branch forward, so the latest branch comparisons now show:
- `feature/driver-worker-ledger`: 80 commits ahead, 2 behind `feature/financial-foundation`.
- `feature/accounting-mobile-foundation`: 23 ahead, 5 behind `feature/accounting-api-foundation`.
- `feature/trial-balance-report`: 9 ahead, 7 behind `feature/accounting-default-chart-completion`.
- `feature/chart-of-accounts-api`: 6 ahead, 4 behind `feature/financial-foundation`.

These counts include the latest branch tips at the time of this refresh. The two commits added to the financial foundation are documentation-only; nevertheless, branch synchronization and a fresh combined CI run remain required before merge.



## Current integration checkpoint — 2026-10-31 (latest review)

This section supersedes the earlier planned sequence where branch state or PR status differs.

### PR #17 — merged into the feature branch (not main)
- PR #17 was explicitly approved by the repository owner and merged into `feature/driver-worker-ledger` using a merge commit.
- Merge commit: `a1e49dd1af323dd9534d83b441aba763e4ac2f5a`.
- It did not modify `main`.

### PR #20 — integration bridge, still Draft and unmerged
- URL: https://github.com/MohammedAli01020/contractor-manager/pull/20
- Base: `feature/financial-foundation`; head: `feature/driver-worker-ledger`.
- Head SHA: `c203a29c3bea1853711960610afd6c726ae1f943`.
- Latest branch comparison: 116 commits ahead / 8 behind; GitHub reports mergeable.
- CI:
  - PR run `41235316574`: success.
  - Head push run `41235312124`: success.
  - Current base push run `41235307730`: success.
  - Integrated backend suite: 165 tests, 0 failures, 0 errors, 0 skipped.
  - MySQL `JournalPostingConcurrencyIntegrationTest`: 8 passed, 0 skipped.
  - V24 populated-data migration guard: 1 passed, 0 skipped.
  - Tenant isolation (6), expense (10), and shift (27) integration tests passed.
- CI provenance qualification: PR run `41235316574` used base SHA `983f32c5bd0d85cf83e26915d565182efb22745e`; the current base branch later advanced to `d59b71fd96412ddb1a0c035f0b7b702d833e5c56`. The only file changed between those base SHAs was `.github/workflows/backend-ci.yml`; the workflow is byte-for-byte identical on the current base and head branches, and the current base push run passed. The backend source under test did not differ, but a new PR merge-result run is required if either backend source or workflow content changes.
- Review follow-up: issue #21 tracks concurrent idempotency-key reuse across different source records and missing comparison of the selected payment account in driver-ledger retries. DB uniqueness prevents duplicate rows, but some races can return an unexpected 5xx. Keep #20 Draft until fixed or explicitly accepted; no merge approval has been given.
- PR #20 description and review comment were updated with this evidence. No production migration was run.

### Current stacked PR state
- **PR #14** API: head `95c474a64aad9c315a335ae5f9076c7aebd83f74`, 14 ahead / 7 behind `feature/financial-foundation`. CI run `41232619556` passed previously; synchronize/revalidate after the foundation integration.
- **PR #15** Flutter accounting: head `e0add33ffa0540304223ea584fed04147468824e`, 23 ahead / 5 behind PR #14 base. Flutter 3.3.3 analyze/tests passed in run `41233389375` (8 tests). Pagination helper tests are unit-level; widget retry/refresh and Android-device smoke tests remain.
- **PR #18** default chart/system account mapping: head `723f5c4500359d95c757f69f83b82dc61d3a26f6`, 14 ahead / 5 behind its current base `fix/treasury-concurrency-hardening`. Prior CI run `41232227739` passed (169 tests). Its base/stack must be reassessed after #20 integration; do not merge against the old base blindly.
- **PR #19** trial balance: head `0a7a104e31165b430a30f5b526da2d07b41e57f2`, 9 ahead / 7 behind PR #18 base. Prior CI run `41232861265` passed. Revalidate after synchronizing with the final #18 base.
- **PR #12** duplicate chart API: head `d502669733fa816be8686387b88a36520f47f5cc`, 6 ahead / 9 behind `feature/financial-foundation`. It still duplicates the canonical chart endpoint in #14; preserve/adapt its tenant-isolation test rather than merging both APIs.
- **PR #13** Flutter AuthBloc tests: head `67c74eaea9cdc37035f18c0623cb95cbf67b0e43`; previously passed run `40848153455`. Re-run against the updated base before merge.

### Current recommended order
1. Resolve or explicitly accept issue #21's idempotency race behavior; do not confuse green CI with complete concurrency coverage.
2. Decide whether #20 can leave Draft only after source review and a fresh PR merge-result validation for the exact current base/head.
3. Once #20 is integrated (only with explicit owner approval), retarget/reconcile #18 → #19 against the resulting financial foundation and re-run backend CI.
4. Reconcile #14 → #15 against the resulting foundation; preserve #12's tenant-isolation test in the canonical #14 API and rerun Flutter CI.
5. Revalidate #13, then complete Android-device smoke tests and the production backup/migration/rollback plan.
6. No PR is to be merged and `main` is not to be changed without explicit approval.
