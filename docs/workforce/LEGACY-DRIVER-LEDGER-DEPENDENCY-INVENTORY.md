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
