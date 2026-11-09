# Legacy Driver Ledger Dependency Inventory

Date: 2026-11-09  
Branch: `feature/workforce-ledger-unification`

## Decision

Do not drop `driver_ledger_entries` or switch the V56 mirror off. The Workforce ledger is still a mirror and the Driver ledger remains the transactional write authority. This inventory records the concrete dependencies verified in the current branch and the gates required before retirement.

## Verified runtime dependencies

| Area | Current dependency | Required migration before retirement |
|---|---|---|
| Shift completion | `ShiftService` inserts EARNING and ADVANCE_RECOVERY into `driver_ledger_entries`; V56 mirrors them | Move posting to a Workforce-native command service with the same transaction boundary, idempotency, journal linkage, and advance-recovery locking |
| Completed-shift correction | `ShiftCorrectionService` loads the original earning from `DriverLedgerEntryRepository` and appends reversal/replacement rows to the legacy ledger | Resolve original/reversal/replacement movements by Workforce identity and stable source references; preserve immutable journal reversals and correction idempotency |
| Payments and advances | `DriverLedgerService` validates balance, locks the Driver projection, posts journal lines, and inserts the legacy movement | Move balance validation and locking to the canonical Workforce member; preserve tenant scoping, idempotency fingerprints, and atomic journal/ledger posting |
| Workforce command adapter | `WorkforceLedgerCommandService` maps Workforce member to Driver and delegates to `DriverLedgerService` | Remove the Driver projection dependency only after native payment/advance commands are implemented and reconciled |
| Legacy HTTP API | `DriverLedgerController` exposes GET statement and POST advances/payments under `/api/v1/drivers/{driverId}/ledger` | Migrate any remaining clients/integrations, then deprecate and remove these routes in a separately reviewed change |
| Database mirror | V56 backfills from legacy rows and mirrors every legacy INSERT; provenance fields retain legacy row and Driver IDs | Keep until a native writer is authoritative and reconciliation proves complete parity; then remove trigger in a separately planned migration |
| Workforce reads | `WorkforceLedgerQueryService` reads `workforce_ledger_entries` by tenant and member | Retain as canonical read API; ensure all reports/export consumers use this path before legacy retirement |

## Verified schema and safety properties

- V56 uses tenant-scoped Driver-to-Workforce mapping; it never assumes Driver and Workforce numeric IDs are equal.
- The backfill uses a LEFT JOIN and a NOT NULL Workforce member column, so an unmapped legacy row fails migration instead of being silently omitted.
- The insert trigger checks that exactly one mirror row was inserted; a missing mapping signals an error and rolls back the source transaction.
- The mirror has independent primary-key allocation and unique provenance by `legacy_source_entry_id`.
- The Workforce mirror has append-only UPDATE/DELETE triggers. Corrections must append compensating movements.
- V56 has a unique per-tenant request key and journal link, and a unique original earning per shift while allowing correction replacement earnings.
- `legacy_driver_id` and `legacy_source_entry_id` are provenance only; they must not become Workforce authorization or balance dimensions.

## Retirement gates (all required)

1. Shift completion and automatic recoverable-advance recovery write natively to Workforce ledger, atomically with journals.
2. Shift corrections locate and compensate Workforce ledger movements without querying the legacy repository.
3. Payments and advances are Workforce-native; balance checks and concurrency locks are scoped to the Workforce member, not Driver.
4. Legacy Driver ledger HTTP routes are removed only after clients/integrations have migrated.
5. Search the complete current source tree, scheduled jobs, reports, exports, and tests for legacy entity/repository/table/API references. Classify migration history and reconciliation SQL separately from runtime dependencies.
6. Run MySQL 8 migration and integration tests, Flutter analysis/build, and CI. Verify idempotency under retries/concurrency and tenant isolation.
7. Run the read-only reconciliation pack per tenant: row counts, provenance, transaction type/amount/effect, journal/shift links, payable balances, recoverable-advance balances, and correction chains. Resolve every mismatch before cutover.
8. Perform cutover and mirror-trigger removal in a separately reviewed, reversible deployment sequence with a verified backup. Do not combine with dropping the legacy table.
9. Drop `driver_ledger_entries` only in a later migration after no runtime code, client, report, or reconciliation procedure depends on it and after explicit approval.

## Scope and validation caveat

This is a verified dependency inventory, not a claim that every repository reference has been exhaustively classified. Migration history and reconciliation SQL intentionally reference the legacy table. The current branch has not been built or integration-tested in this environment; no migration or production database was executed or changed.

## Native-write schema preparation (V58)

- V58 makes `legacy_source_entry_id` and `legacy_driver_id` nullable as a pair.
- Existing mirrored rows retain both provenance values; a future native Workforce movement may set both to NULL.
- A CHECK constraint rejects partially populated provenance (only one of the two values set).
- The V56 legacy INSERT trigger remains enabled and unchanged. Current application writers still use the Driver ledger.
- The nullable unique provenance index continues to prevent duplicate legacy source links while allowing multiple native rows with NULL provenance under MySQL semantics.
- This is schema preparation only, not a writer cutover. No Workforce-native financial command has been enabled by V58.

## Native provenance schema test

- `DriverLedgerIdempotencyIntegrationTest.workforceLedgerSchemaAcceptsNativeMovementWithoutLegacyDriverProvenance` inserts a balanced journal whose lines reference the Workforce member without a Driver ID, then inserts a Workforce ledger movement with both legacy provenance columns NULL.
- The test asserts the movement is visible through the canonical Workforce repository, contributes to the Workforce balance, and has no corresponding Driver ledger row.
- This is a schema/read-path capability test only. It does not switch shift completion or any production command to native writes.
- The integration test has been added but has not been executed in this environment.

- The same integration test also attempts a row with only `legacy_driver_id` populated and expects a data-integrity failure, exercising the V58 paired-provenance CHECK constraint.

## JPA entity alignment (V58)

- Reviewed `com.contractor.app.workforce.domain.WorkforceLedgerEntry` on this branch.
- Both `legacySourceEntryId` and `legacyDriverId` are nullable wrapper `Long` fields, mapped to their matching nullable database columns, and marked `updatable = false`.
- No entity change is required for V58: the mapping already permits both values to be null for a Workforce-native movement while keeping provenance immutable after insertion.
- Keep both columns during the compatibility and reconciliation period. They are historical traceability fields, not canonical identity. Removing them is a separate schema decision and must wait until legacy references, reconciliation needs, and retention requirements have been reviewed.
- The next functional cutover work remains shift completion/recovery and completed-shift corrections; do not change the ledger writer until their locking, idempotency, journal, and correction-chain behavior can be preserved in one transaction.

## Transaction and lock-order findings (2026-11-09)

The following details were verified directly in `ShiftService`, `ShiftCorrectionService`, and `DriverLedgerService`; they are cutover constraints, not implementation changes.

### Shift completion

- `finish` first locks the shift row. For a positive wage it then locks the tenant-scoped Driver row, initializes/loads the chart accounts, posts the wage journal, and inserts the EARNING movement.
- Only after inserting the earning does it calculate the recoverable-advance balance, capped at the current wage, and—when positive—posts the recovery journal and ADVANCE_RECOVERY movement.
- The wage journal, earning row, recovery journal, recovery row, shift completion status, and audit event share the method transaction. A native implementation must preserve this all-or-nothing boundary and the effective shift -> Driver/Workforce lock ordering.
- Zero-wage completion deliberately creates neither an earning nor an automatic recovery. A native writer must not accidentally recover an advance against a zero earning.
- The current recovery query is keyed by Driver ID and sums RECOVERABLE_ADVANCE minus ADVANCE_RECOVERY. Native cutover must use the Workforce member balance with identical semantics and must serialize against payments/advances on the same canonical member.

### Completed-shift corrections

- `correct` reserves/rechecks idempotency around a tenant-scoped shift-row lock, validates the existing posted wage journal and its two journal lines, then locks the Driver row before checking later payments/advances/recoveries.
- The current safety policy rejects correction if the shift has an ADVANCE_RECOVERY, or if a payment, earned-balance advance, or recovery was created after the shift finished. This is an intentional restriction; do not silently broaden correction eligibility during ledger migration.
- Original earning lookup, reversal append, and replacement earning append currently use `DriverLedgerEntryRepository`. Reversal/replacement journal postings and correction-link IDs are stored in the correction record. A native path must resolve and validate the entire correction chain by tenant + Workforce member + shift/journal/source references, preserve append-only movements, and retain the existing idempotency behavior.
- Corrections do not currently reverse/recreate automatic advance recoveries. The rejection gate for shifts with recovery must remain until a separately designed settlement workflow handles that case.

### Payments and advances concurrency

- `DriverLedgerService.pay` and `advance` initialize default accounts before acquiring the Driver row lock, then read the ledger balance after the lock under READ_COMMITTED. This prevents a stale pre-lock balance snapshot and serializes competing movements for one Driver.
- The Workforce command adapter currently resolves a Driver projection and delegates to those methods; the V56 trigger remains the only mirror writer.
- Native payments/advances must acquire the canonical Workforce member lock and calculate both payable and recoverable-advance balances from Workforce rows after that lock. Preserve the existing request fingerprint inputs, idempotency reservation, journal account validation, and single transaction; do not implement simultaneous writes to both ledgers.

### Safe next implementation sequence

1. Design one canonical Workforce member lock protocol shared by shift completion, payment, advance, and correction; document a global lock order including Shift, WorkforceMember, and Equipment before changing code.
2. Implement native payment/advance commands first or provide a shared native balance/command primitive, retaining the Driver endpoints only as compatibility adapters during transition.
3. Move shift completion and recoverable-advance recovery to native Workforce rows in one transaction, with concurrency/idempotency tests for payment-vs-finish and advance-vs-finish.
4. Move correction lookup and compensating movements to Workforce ledger, retaining the current rejection gates and verifying multi-correction chains.
5. Only after full source/client/report inventory, MySQL migration tests, reconciliation, and explicit cutover approval, remove the mirror trigger. Drop the legacy table in a later separately approved change.

No application code, migration, or database was changed as part of this review. These findings are source inspection only; builds and tests remain unrun.

## Database-enforced correction dependencies (verified 2026-11-09)

The application repository is not the only legacy dependency. The migration history installs database triggers that directly query `driver_ledger_entries`:

- V29's `trg_shifts_protect_completed` validates the old, reversed, and replacement earning rows against the legacy table before allowing a completed-shift correction.
- V35's `trg_driver_ledger_validate_shift_correction` authorizes correction reversal and replacement inserts by joining `shift_corrections`, posted correction journals, and legacy earning rows.
- V30 adds `CORRECTION_REVERSAL` to the legacy table's transaction-type CHECK constraint.
- `shift_corrections.old_earning_ledger_entry_id`, `reversed_earning_ledger_entry_id`, and `replacement_earning_ledger_entry_id` currently store legacy ledger primary keys. They are not Workforce ledger IDs, even though a mirror row can be found through `legacy_source_entry_id`.

Consequently, changing only `ShiftCorrectionService` to insert Workforce rows would fail to preserve the database's authorization/integrity envelope. A future cutover must introduce explicit Workforce movement references (or another stable tenant-scoped reference design), replace the V29/V35 trigger logic in a reviewed forward migration, and test both allowed and rejected correction paths against MySQL 8. Do not reinterpret the existing correction ID columns as Workforce IDs.

## Lock-order caveat

The current code does not yet have one uniform lock order across every workflow:

- Shift start: Driver -> WorkforceMember -> Equipment.
- Shift finish: Shift -> Driver (for positive wage).
- Completed-shift correction: Shift -> Driver.
- Driver payment/advance: Driver (after default-account initialization).

This is not a reason to add an isolated Workforce lock to one command. Before native cutover, define and test a single cross-workflow lock protocol that accounts for shift serialization, canonical member serialization, and equipment reservation. Update all competing commands consistently in one coordinated change and add opposing-order concurrency tests to detect deadlocks. The existing Driver-first comments describe the compatibility-phase order; they are not a complete canonical lock protocol.

## Workforce correction references (V59)

- V59 adds three nullable canonical references to `shift_corrections`: original earning, reversal, and replacement earning Workforce ledger row IDs. The existing `*_earning_ledger_entry_id` columns remain legacy Driver ledger IDs and are not reinterpreted.
- V59 adds tenant-scoped composite foreign keys to `workforce_ledger_entries(tenant_id, id)`. The composite unique index is intentional because the correction references must be tenant-safe.
- Existing APPLIED correction audit rows are not backfilled or updated. V44's guard rejects updates to APPLIED rows; preserving their immutable historical state takes priority. Their original legacy IDs remain valid, and their new Workforce references stay NULL.
- New correction creation resolves the original mirrored row through `legacy_source_entry_id` and validates tenant, Workforce member, legacy Driver provenance, shift, journal, type, amount, and balance effect before recording its canonical ID.
- Newly appended reversal/replacement movements are resolved immediately after the legacy insert (the V56 trigger mirrors them in the same transaction), and their Workforce IDs are stored in the correction row.
- V59 adds database guards on correction INSERT and the APPLYING -> APPLIED transition. They validate the exact canonical rows against the expected legacy provenance, Driver-to-Workforce mapping, shift, journal, movement type, amount, and effect. V44 continues to validate legacy references and protect applied audit rows.
- This is still a compatibility-stage change: legacy remains the writer, V56 remains enabled, and no ledger cutover or table retirement occurs.
- Integration assertions were added for positive corrections, repeated correction chains, zero-wage transitions, and preservation of historical correction rows. They have not yet been executed in this environment.
- A runtime cross-tenant rejection test now creates a real Workforce earning in a second tenant through the public shift lifecycle, then attempts to attach that row to an APPLYING correction in the first tenant. It expects the tenant-scoped replacement FK to reject the update and verifies the reference remains NULL. The test uses a zero-wage shift to avoid inventing financial rows; driver API validation explicitly permits zero rates (only negative rates are rejected). The test itself remains unexecuted.

## V59 canonical correction references — migration review correction

- The authoritative migration is `V59__add_workforce_references_to_shift_corrections.sql`. It already existed on this branch before the duplicate file was accidentally introduced.
- V59 creates the three nullable Workforce reference columns, a tenant-scoped unique key on `(tenant_id, id)`, and composite foreign keys from the correction references to Workforce ledger rows.
- V59 also installs INSERT and APPLYING-to-APPLIED guards. They validate the canonical original, reversal, and replacement rows against legacy source IDs, Driver-to-Workforce mapping, shift, journal, type, amount, and balance effect.
- The duplicate file `V59__add_workforce_ledger_references_to_shift_corrections.sql` was removed immediately after discovery. There must be exactly one migration for version 59. No replacement V60 is needed for the same schema.
- Historical APPLIED corrections are not backfilled: V44 protects their immutability. Their legacy references remain intact, and their new Workforce reference columns remain NULL.
- `ShiftCorrectionIntegrationTest` now explicitly asserts that correction N+1 uses correction N's replacement Workforce row as its old earning reference, and that changing a canonical reference after APPLIED is rejected by the audit immutability guard.
- `ShiftCorrectionSnapshotMigrationIntegrationTest` now asserts that all three canonical references have composite `(tenant_id, id)` foreign keys, and that the referenced composite unique index exists. This verifies the schema-level tenant boundary.
- The integration tests also cover positive corrections, repeated correction chains, zero-wage transitions, and preservation of historical correction rows. They have not been executed in this environment.
- This remains a compatibility-stage design: legacy remains the writer, V56 remains enabled, and no ledger cutover or table retirement occurs.


### V44/V59 MySQL 8 compatibility review

- V44 remains the authoritative legacy correction guard: it rejects every UPDATE or DELETE of an APPLIED correction and validates legacy journal/Driver-ledger references on APPLYING-to-APPLIED.
- V59 adds separate BEFORE INSERT and BEFORE UPDATE guards. The update guard validates the canonical Workforce mirror during APPLYING-to-APPLIED; after APPLIED, V44 rejects all updates, including attempts to alter the new Workforce reference columns.
- V56 declares both `workforce_ledger_entries.id` and `tenant_id` as signed `BIGINT NOT NULL`; V59 uses the same signed `BIGINT` type for the three nullable reference columns and adds the referenced composite unique key `(tenant_id, id)`. The composite FK column order matches the unique key.
- V59's trigger queries other tables (`workforce_ledger_entries` and `drivers`) and does not query the mutating `shift_corrections` table, avoiding MySQL's mutating-table restriction.
- The two UPDATE guards are separate MySQL triggers with distinct names. V59 does not drop or replace V44's audit guard; both constraints remain active.
- GitHub Actions returned no PR workflow runs and no status checks for the current branch commit. No MySQL container, Flyway migration, or test suite was executed during this review. Runtime compatibility therefore remains unverified until the disposable MySQL 8 integration test runs.


### Cross-tenant correction-reference test review (2026-11-09)

- Reviewed `correctionCannotReferenceAnotherTenantsWorkforceLedgerEntry` against the driver request validation, shift lifecycle helpers, and V51/V44/V59 trigger rules.
- Zero driver rates satisfy `@DecimalMin("0")`; V41/V44/V51 permit the zero-wage completed-shift/correction snapshot without zero-value journal or ledger rows. The helper's snapshot values match the completed SHIFT-basis compensation snapshot.
- The test's foreign Workforce row is produced by normal completion in a separately registered tenant. Its update is rejected by the composite `(tenant_id, id)` FK, while the local correction remains unchanged.
- The assertion now expects Spring's `DataIntegrityViolationException` translation for an integrity-constraint violation, rather than the generic `UncategorizedSQLException` type.
- This is source-level review only. No MySQL 8 execution, Flyway run, or test result is claimed; GitHub currently reports no workflow runs or status checks for the reviewed commit.

## CI validation update (2026-11-09)

- GitHub Actions run [42610054263](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42610054263) completed successfully for branch `feature/workforce-ledger-unification` at commit `e8f599b60fda3f68abc7c73300dcac6c30f7a7da`.
- The `Backend Maven Tests` job and its required execution gates completed successfully, including the WF12B operational projection migration, MySQL accounting, V24 migration, financial idempotency, shift wage/zero-cost equipment, chart tenant isolation, trial balance, Workforce member/role/assignment, compensation agreement, WF-08A/WF-08B correction, and WF12C Workforce identity migration test gates.
- The run followed the test fixes in `805c4b7` (native-provenance insert binding), `9d4999c` (correction-chain row count), and `e8f599b` (MySQL CHECK-constraint exception translation).
- This successful CI run supersedes the earlier statements above that these specific integration tests had not yet been executed. It does not establish that every test or build target outside this Backend Maven CI workflow has passed.
- The current architecture remains unchanged: Driver ledger is still the application write authority, V56 mirror remains active, and no legacy table or trigger has been removed. No production database was accessed or changed.

## Native payment/advance cutover gate — 2026-11-09

A proposed isolated switch of the Workforce payment/advance endpoint to append native rows was reviewed and deliberately not retained. Source inspection found that this would be unsafe while the legacy Driver payment/advance endpoints remain active:

- The legacy endpoints still call `DriverLedgerService`, calculate balances from `driver_ledger_entries`, and insert rows mirrored by V56.
- A native Workforce payment/advance would update only `workforce_ledger_entries`. A later legacy endpoint call would calculate a stale Driver balance that excludes that native movement and could permit an overpayment or an earned-balance advance beyond the true balance.
- A member-only lock is also insufficient during the transition because shift finish and correction still serialize through the Driver projection. The transitional order must remain default-account initialization -> Driver projection lock (when one exists) -> Workforce member lock. Generic Workforce members without a Driver projection still require the canonical member lock.
- Therefore payment/advance writer cutover must be coordinated across both HTTP route families in one transaction-safe change. The legacy Driver routes must become compatibility adapters to the same canonical command (with an explicitly reviewed response/ID compatibility contract), or be safely retired after client migration. They must not remain independent writers.
- The canonical writer must also preserve historical retry behavior for existing mirrored rows, request-key uniqueness, fingerprint checks, account validation, journal posting, and concurrency across the two route families.

The isolated implementation was reverted before retaining a split-writer state. The Workforce entity now has a constructor capable of creating native rows with both provenance fields NULL; this is schema/entity preparation only and is not called by the active command service. Current production behavior on this branch remains unchanged: Workforce payment/advance commands still delegate to the legacy Driver writer, and V56 remains the mirror.

Next safe implementation unit: introduce a shared canonical payment/advance command and route both Workforce and Driver controllers through it, preserving or explicitly versioning the legacy response contract; add mixed-route race/retry tests before enabling the cutover. Do not remove the Driver projection lock until shift completion and correction have moved to the same canonical lock protocol.

### Cross-route idempotency regression coverage — 2026-11-09

Added an integration test that submits the same payment first through the Workforce member route and then through the legacy Driver route. It asserts that the retry reuses the original Driver-ledger movement and journal, and that exactly one mirrored Workforce row exists with complete legacy provenance. This protects the current compatibility behavior while the shared-writer cutover is designed. The test has been committed but has not yet been observed in a new CI run.

### Mixed-route payment concurrency validation — 2026-11-09

- Commit `d158145fda77386b04b1de77f6407cfee1f07c45` adds a mixed-route, distinct-key payment race: two concurrent requests each attempt to pay 300 from the same 500 earned balance, one through the Workforce route and one through the Driver route.
- GitHub Actions run [42615462847](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42615462847) completed successfully. The full Backend Maven Tests job and all required migration/integration execution gates passed.
- The test asserts exactly one 300 payment in each ledger, a remaining payable balance of 200 in both projections, complete legacy provenance on the Workforce mirror, and exactly one corresponding payment journal.
- This validates the current Driver-row serialization and V56 mirror under the tested mixed-route race. It does not make Workforce ledger the writer.

### Mixed-route advance concurrency validation — 2026-11-09

- Commit `6d1229d9869f75b8264989f61e9dd4fb5a8e6332` adds two integration tests to `DriverLedgerIdempotencyIntegrationTest`.
- `AGAINST_EARNED`: concurrent 300 advances via Workforce and Driver routes against 500 earned balance; expects one 201 and one 400, one mirrored ADVANCE movement, and 200 remaining payable.
- `RECOVERABLE`: concurrent 300 advances via both routes; expects two 201 responses, two legacy movements and two Workforce mirrors, unchanged payable balance of 500, and recoverable-advance balance of 600.
- GitHub Actions run [42615938860](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42615938860) was started for this commit. Its result is pending at the time of this update; these new tests must not be described as passed until that run completes successfully.
- These tests continue to validate the compatibility architecture only: Driver ledger remains the sole application writer and V56 remains the mirror.

## Mixed-route advance concurrency CI result and canonical lock protocol (2026-11-09)

- GitHub Actions run [42615938860](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42615938860) completed with conclusion `success` on 2026-11-09. The backend test job completed successfully, including the mixed-route advance concurrency tests added in commit `6d1229d9869f75b8264989f61e9dd4fb5a8e6332`.
- The tests cover two distinct invariants: concurrent AGAINST_EARNED advances cannot overdraw the payable balance, while concurrent RECOVERABLE advances increase the recoverable balance without reducing earned/payable balance. Both tests exercise Driver and Workforce HTTP command routes against the same legacy writer and V56 mirror.
- This validates the compatibility-stage shared writer and its serialization behavior; it does not validate a Workforce-native writer or authorize trigger/table retirement.

### Proposed cross-workflow lock hierarchy for the native cutover

Use this relative order whenever a transaction needs more than one of these resource classes:

1. Shift row (only for commands operating on an existing shift).
2. Legacy Driver operational row (only while the compatibility projection remains part of the command).
3. Canonical WorkforceMember row.
4. Equipment row.

Within each class, acquire multiple rows in ascending primary-key order. A command may acquire only the subset it needs, but must never acquire a later class and then request an earlier class. Non-locking validation reads should happen after the relevant locks when they could otherwise establish a stale MySQL REPEATABLE READ snapshot.

Expected command shapes during the coordinated transition:

| Command | Ordered locks |
|---|---|
| Start shift | Driver -> WorkforceMember -> Equipment |
| Update active shift / reinstate active shift | Shift -> Drivers (sorted) -> WorkforceMembers (sorted) -> Equipment (sorted) |
| Finish shift / correct completed shift | Shift -> Driver -> WorkforceMember |
| Payment / either advance type, compatibility phase | Driver -> WorkforceMember |
| Payment / either advance type, after native cutover | WorkforceMember |
| Identity update for a linked member | Driver -> WorkforceMember |

Important constraints:

- The hierarchy is a target protocol, not a claim that every current method already follows it. Current payment/advance and finish paths still need the WorkforceMember lock to be added in a coordinated implementation; active-shift reinstate currently needs the WorkforceMember lock before Equipment.
- Keep default chart initialization before acquiring the resource locks where it may create/read chart rows. Do not introduce a chart/resource lock cycle.
- Preserve READ_COMMITTED for monetary balance checks so the aggregate is read after the canonical member lock is acquired.
- A future native payment/advance writer must lock WorkforceMember even if it no longer locks Driver. During the transition, commands that touch both must keep Driver before WorkforceMember.
- Do not add the WorkforceMember lock to one isolated endpoint. Update all competing command paths together and add opposing-order MySQL concurrency tests covering start-vs-finish, update/reinstate-vs-finish, payment-vs-finish, earned-advance-vs-finish, recoverable-advance-vs-finish, and identity-update-vs-financial-command.
- These tests must assert both no deadlock/timeout and the final journal, ledger, shift, and resource-reservation invariants. Retry/idempotency behavior and tenant scoping remain mandatory.
- Before moving any writer, review the exact Spring transaction boundaries and SQL lock acquisition order in MySQL 8. Repository method call order alone is not sufficient if lazy flushes or database triggers acquire additional rows.

No runtime code or schema was changed by this documentation update. The next implementation should be the coordinated lock-protocol change plus its MySQL concurrency tests, not an isolated native writer.

## Canonical member lock implementation and CI result (2026-11-09)

- Implemented the compatibility-phase member lock in DriverLedgerService: payment and both advance types now acquire the tenant-scoped Driver row first, then the linked WorkforceMember row, before reading the ledger balance and posting.
- Implemented the same Driver -> WorkforceMember order in ShiftService.finish and ShiftService.reinstate; reinstate acquires the member lock before Equipment.
- Implemented the same Shift -> Driver -> WorkforceMember order in ShiftCorrectionService.correct.
- The implementation is on this branch in commits:
  - d6dcd8e0eb27fd344b0c755f7a38d9901ac4d531 — DriverLedgerService payment/advance member lock.
  - a05e80b41402fa3c564734e41114b80b9eb70768 — ShiftService finish/reinstate member lock.
  - e4d38f6130edd9f9306c14e3f597d750b9d8cbed — ShiftCorrectionService member lock.
- GitHub Actions run [42616572085](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42616572085), for head commit e4d38f6130edd9f9306c14e3f597d750b9d8cbed, completed successfully. The Backend Maven Tests job passed.
- This CI result validates compilation and the existing backend test suite after the lock additions. It does not yet prove the new cross-workflow lock order is deadlock-free under simultaneous finish/payment/advance/identity-update traffic; dedicated opposing-order MySQL concurrency tests remain the next gate.
- Current command lock shapes now implemented in the compatibility phase: start = Driver -> WorkforceMember -> Equipment; finish/correction = Shift -> Driver -> WorkforceMember; payment/advance = Driver -> WorkforceMember; reinstate = Shift -> Driver -> WorkforceMember -> Equipment. Identity update and any other command that can touch these resources still require explicit verification against this order.
- The Driver ledger remains the sole application writer, the Workforce ledger remains the V56 mirror, and all legacy dependencies listed above remain active. No trigger, route, table, or historical provenance column was removed.

### Cross-workflow concurrency tests — CI result (2026-11-09)

- Commit `3cbcbd4460cedb097d9959973b6bb6b5415f819e` corrected the expected balance equation in `concurrentShiftFinishAndRecoverableAdvanceSerializeWithoutDeadlock`: the test now accounts for the amount automatically recovered from the recoverable advance when the shift earning is posted.
- The CI run for that intermediate commit, [42624407551](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42624407551), was cancelled after a newer commit triggered a replacement run. It is not counted as a pass or failure.
- Commit `99b07cade4753a0168f7a29f13ceb6ded0c02256` adds `concurrentCanonicalIdentityUpdateAndPaymentSerializeWithoutDeadlock`, racing a canonical Workforce identity update against a legacy Driver payment and asserting both operations, synchronized identity projections, ledger mirror parity, and final balance.
- GitHub Actions run [42624589685](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42624589685) completed with conclusion `success` for commit `99b07cade4753a0168f7a29f13ceb6ded0c02256`. The complete `Backend Maven Tests` job passed, including the Maven test execution and all required migration/integration execution gates.
- Because the successful run is on the commit after the corrected recoverable-advance test, it validates both the corrected finish-vs-recoverable-advance test and the new identity-update-vs-payment test as part of the backend suite.
- These results validate the tested compatibility-phase lock and mirror behavior only. Driver ledger remains the application write authority; V56 remains enabled; no legacy table, trigger, route, or provenance column was removed.


### Completed-shift correction vs payment concurrency — CI result (2026-11-09)

- Commit `321cb89ee262e04b6f8e0ef08bfa33ca01ddf663` adds `concurrentCompletedShiftCorrectionAndPaymentSerializeWithoutDeadlock` to `ShiftIntegrationTest`.
- The test races a completed-shift wage correction against a payment for the same driver. If correction wins, the replacement wage is the basis for the payment and the resulting payable balance is 110.00. If payment wins, correction is rejected with HTTP 409 and the original wage remains the basis for the payment.
- It also asserts exactly one payment ledger movement, correction/reversal rows only when correction is applied, and equality between the Driver ledger balance and the V56 Workforce mirror.
- GitHub Actions run [42632185443](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42632185443) completed with conclusion `success` on 2026-11-09. The complete `Backend Maven Tests` job passed, including the newly added concurrency test and the required migration/integration execution gates.
- This is compatibility-phase validation only: Driver ledger remains the sole application writer and V56 remains the Workforce mirror. No legacy table, trigger, route, or provenance column was removed.
