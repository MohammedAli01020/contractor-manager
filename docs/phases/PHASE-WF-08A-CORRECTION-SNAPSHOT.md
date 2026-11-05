# WF-08A — Audited Compensation Correction Snapshot

Status: design checkpoint on `feature/wf08a-correction-snapshots`.
Base: `integration/stabilization-2026-11`.
This phase is schema and database-authorization work only. It does not enable HOUR or QUANTITY corrections in the API.

## Accepted business rules

- `SHIFT` corrections retain the existing audited correction path.
- `HOUR` and `QUANTITY` corrections are enabled only after WF-08A database protection and WF-08B transactional posting are complete.
- A completed shift's compensation basis, contractual rate, unit, currency, measured value, and calculated amount are frozen snapshots.
- The correction request supplies the new measured value, not a client-calculated wage.
- The server calculates the replacement amount from the shift's frozen rate and rounds money to two decimal places using the same established policy as shift completion.
- A zero frozen rate is valid. A measured value must remain positive. A zero calculated wage completes without zero-value journal or worker-ledger entries.
- Posted journal entries remain immutable. A positive old wage is reversed; a positive replacement wage is posted. Zero sides create no zero-value posting.
- The worker identity remains unchanged in the first release.
- Existing payment/advance/recovery safety restrictions remain in force until a separate settlement design supports them.

## Existing implementation findings

- `shifts` already stores `compensation_basis`, `compensation_rate`, `compensation_unit_code`, `compensation_currency_code`, `compensation_calculated_amount`, `actual_hours`, and `actual_quantity`.
- `shift_corrections` currently snapshots business fields and wage/journal/ledger references, but does not snapshot compensation basis/rate/unit/currency/calculated amount or old/new actual measurements.
- The current service explicitly rejects `HOUR` and `QUANTITY`.
- The current request accepts `driverWage`; that must not be reused as the source of truth for measured-basis corrections.
- V46 protects completed measurements by rejecting direct changes. It must be extended only to authorize a fully matched APPLYING correction row.
- The correction audit guard (introduced in V44 and further adjusted by later migrations) must also make every new snapshot column immutable after insertion, apart from the explicitly permitted financial-reference/status transition.

## Required snapshot columns

Add nullable old/new pairs to `shift_corrections` for backwards compatibility with historical SHIFT correction rows:

| Snapshot | Old | New |
|---|---|---|
| Basis | `old_compensation_basis` | `new_compensation_basis` |
| Rate | `old_compensation_rate` | `new_compensation_rate` |
| Unit | `old_compensation_unit_code` | `new_compensation_unit_code` |
| Currency | `old_compensation_currency_code` | `new_compensation_currency_code` |
| Calculated amount | `old_compensation_calculated_amount` | `new_compensation_calculated_amount` |
| Actual hours | `old_actual_hours` | `new_actual_hours` |
| Actual quantity | `old_actual_quantity` | `new_actual_quantity` |

Use the same SQL precision/scale as the corresponding `shifts` columns. Preserve NULL for the non-applicable measurement (hours for non-HOUR; quantity for non-QUANTITY). Keep rate and unit/currency frozen: correction changes the measured value, not the historical contract.

## Database authorization requirements

1. The migration must be additive and forward-only; never edit V1–V46.
2. New columns are nullable so old correction rows remain valid and readable.
3. A measured-basis correction is authorized only when exactly one tenant-scoped `APPLYING` correction row matches:
   - shift id, tenant id, expected version;
   - every old business field and every old compensation snapshot;
   - every requested new business field;
   - unchanged worker id, basis, rate, unit and currency;
   - the new measurement and server-calculated wage;
   - the old and replacement journal/ledger references.
4. The database must reject direct updates to completed `actual_hours`, `actual_quantity`, `driver_wage`, `compensation_calculated_amount`, rate, basis, unit or currency without that exact authorization.
5. The correction audit row must reject changes to old/new snapshot values after insertion. Only the existing APPLYING-to-APPLIED transition and permitted financial reference population may occur.
6. The trigger must validate positive-measurement semantics and exact rounding consistency:
   - HOUR: `new_driver_wage = ROUND(old_compensation_rate * new_actual_hours, 2)`.
   - QUANTITY: `new_driver_wage = ROUND(old_compensation_rate * new_actual_quantity, 2)`.
   - Zero rate is valid; measurement must be positive; no zero-value journal or ledger row.
7. The trigger must require the matching posted reversal for a positive old wage and matching posted replacement for a positive new wage. Zero sides must have NULL journal/ledger references.
8. Preserve the existing SHIFT correction behavior and zero-wage rules.

## Implementation sequence and release gate

- First add the snapshot columns and map them in `ShiftCorrection`.
- Then extend the correction insert path to capture the old snapshot and the proposed new measurement while retaining frozen rate/unit/currency.
- Replace the V46 protection trigger in a new migration with an exact APPLYING-row authorization. Extend the audit guard in a new migration so the new snapshot cannot be edited after insertion.
- Add migration tests against a clean database and an upgraded V46 database. Verify trigger creation, historical row compatibility, direct-SQL rejection, tenant isolation, and zero-wage behavior.
- Do not remove the service's HOUR/QUANTITY rejection in WF-08A. Enable those bases only in WF-08B after journal/ledger correction tests pass.
- No production migration or merge is authorized by this phase.
