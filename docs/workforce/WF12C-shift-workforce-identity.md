# WF12C — Shift Workforce identity

## Purpose

Make `workforce_members` the canonical person identity referenced by every shift while retaining `shifts.driver_id` as a compatibility projection during the transition.

## Migration V54

1. Add a unique parent key on `drivers(id, tenant_id, workforce_member_id)`.
2. Add `shifts.workforce_member_id`.
3. Backfill it only through the tenant-matched `shifts.driver_id = drivers.id` relationship.
4. Make the column non-null. The migration intentionally fails if any historical shift cannot be resolved; it must not invent or guess a person.
5. Add a tenant-safe FK to `workforce_members(id, tenant_id)`.
6. Add a composite FK from `shifts(driver_id, tenant_id, workforce_member_id)` to the exact driver projection tuple. This prevents mismatching a driver row and a different Workforce member at the database boundary.

The application sets the canonical identity from the already tenant-scoped, locked/validated `Driver` on shift creation and editable shift updates. The request continues to accept `driverId` for compatibility; clients cannot choose an unrelated `workforceMemberId`. Shift responses add `workforceMemberId`.

## Preserved behavior and data

- `driver_id` is not renamed, dropped, or repointed.
- No driver, shift, journal, journal line, or driver-ledger row is deleted.
- No posted accounting entry, wage snapshot, compensation agreement, or historical amount is modified.
- No historical compensation is recalculated.
- Existing APIs continue to accept and return `driverId`; `workforceMemberId` is additive.

## Validation

The shift integration tests assert that:
- the API response and persisted shift both carry the Workforce ID linked to the selected driver;
- MySQL rejects a shift whose driver projection is paired with another Workforce member;
- direct repository test fixtures populate the now-required canonical identity, so uniqueness tests still exercise their intended indexes.

## WF06 rollout checks

Before applying V54 to the isolated WF06 database, take a fresh dump and run these read-only preflight checks:

```sql
SELECT COUNT(*) AS shifts_without_driver
FROM shifts s
LEFT JOIN drivers d ON d.id = s.driver_id AND d.tenant_id = s.tenant_id
WHERE d.id IS NULL;

SELECT COUNT(*) AS drivers_without_workforce_identity
FROM drivers
WHERE workforce_member_id IS NULL;

```

Both preflight counts must be zero before migration. After V54, verify every shift has a non-null Workforce ID and run this read-only consistency check:

```sql
SELECT COUNT(*) AS shift_identity_mismatches
FROM shifts s
JOIN drivers d ON d.id = s.driver_id AND d.tenant_id = s.tenant_id
WHERE s.workforce_member_id <> d.workforce_member_id;
```

The mismatch count must be zero, and the composite FK must reject mismatched identities. Re-run the existing start/finish/correction and financial-balance tests. Do not apply this migration to production until the isolated migration and application test pass.

## Deliberately out of scope

This phase does not migrate driver-ledger entries, journal dimensions, payments, advances, recoveries, or idempotency keys to Workforce IDs. Those require separate reviewed stages. It also does not remove the legacy Driver projection.

## Active-shift uniqueness — V55 verification (2026-11-09)

Migration `V55__prevent_duplicate_active_workforce_shifts.sql` was applied successfully to the isolated local WF06 test database (`contractor_manager_wf06_test`, MySQL 8.0.44).

- A verified pre-migration database dump was taken before applying V55. The dump is retained locally; no production database was involved.
- The migration history records V55 as successful. The generated `active_workforce_member_id` column is STORED, and `uq_shifts_one_active_workforce_member` is a tenant-scoped unique index.
- The test database retained all 15 shifts: 13 COMPLETED and 2 IN_PROGRESS. A duplicate-active-Workforce-member preflight query returned no rows.
- GitHub Actions Backend CI run [780](https://github.com/MohammedAli01020/contractor-manager/actions/runs/42580463622) passed. The run covered 272 backend tests, including the active-shift concurrency tests and the migration integration test proving the Workforce unique constraint independently. That integration test drops the legacy Driver unique index only inside its isolated test schema; it does not alter the WF06 test database.
- Manual API check returned HTTP 409 `ACTIVE_SHIFT_CONFLICT` when attempting a conflicting start using an already-active legacy Driver ID. This confirms the API conflict response, but that particular request was rejected by the legacy Driver guard; it is not presented as a direct manual proof of the Workforce guard.
- Manual Flutter test confirmed that workers already assigned to active shifts appear unavailable and cannot be selected when starting another shift.

**Result:** the active-shift protection is verified at the database, CI/integration-test, API-conflict, and manual Flutter availability levels. The legacy Driver compatibility path remains intentionally present. This verification does not authorize deleting Driver tables, columns, APIs, ledger structures, or historical data.
