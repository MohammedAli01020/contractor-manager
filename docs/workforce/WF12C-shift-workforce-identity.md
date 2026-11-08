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
