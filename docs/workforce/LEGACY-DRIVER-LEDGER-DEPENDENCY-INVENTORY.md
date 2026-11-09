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

