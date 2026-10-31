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
