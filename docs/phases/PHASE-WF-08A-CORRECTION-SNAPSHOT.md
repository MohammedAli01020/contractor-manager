# WF-08A — Audited Compensation Correction Snapshot

Status: PR #41 is open for review from `feature/wf08a-correction-snapshots` into `integration/stabilization-2026-11`; not merged and not approved for production.
Base: `integration/stabilization-2026-11`.
This phase is schema and database-authorization work only. It does not enable HOUR or QUANTITY corrections in the API.

## Accepted business rules

- `SHIFT` corrections retain the existing audited correction path.
- `HOUR` and `QUANTITY` corrections are enabled only after WF-08A database protection and WF-08B transactional posting are complete.
- A completed shift's compensation basis, contractual rate, unit, currency, measured value, and calculated amount are captured in immutable before/after correction snapshots.
- For the existing `SHIFT` wage-override path, `Shift.setDriverWage` updates `compensation_calculated_amount`; it updates `compensation_rate` only when `wage_source = LEGACY_DRIVER_RATE`. Agreement-backed contractual rates and identity remain frozen.
- For future `HOUR`/`QUANTITY` corrections, the contractual rate, unit, and currency remain frozen; only the applicable measured value and server-calculated amount may change.
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
- V46 protects completed business/measurement fields and authorizes only a fully matched APPLYING correction row for the existing SHIFT correction path.
- V48 adds immutable snapshot-update protection; V49 replaces the snapshot-insert validator with the final SHIFT-only semantics. The existing V44 audit guard still controls the APPLYING-to-APPLIED transition and financial-reference population.

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

Use the same SQL precision/scale as the corresponding `shifts` columns. Preserve NULL for the non-applicable measurement (hours for non-HOUR; quantity for non-QUANTITY). The existing SHIFT override is a separate compatibility rule: a legacy driver-rate snapshot follows the corrected wage, while an agreement-backed rate stays frozen.

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

## Current implementation on WF-08A

- V47 adds nullable old/new compensation snapshot columns.
- V48 adds snapshot insert validation and immutable snapshot update protection.
- V49 tightens SHIFT snapshot semantics and explicitly blocks HOUR/QUANTITY correction rows.
- V50 protects completed-shift compensation metadata from direct SQL changes unless the exact APPLYING correction snapshot authorizes the update.
- The integration test verifies old/new SHIFT wage snapshots and rejects direct changes to completed compensation rate/calculated amount.
- Backend CI completed successfully on code commit `fa54bccc6b7781cbf0598fbdcd646cfb62166c37` in push run `42223135429` and PR run `42223141907`. Both runs completed with `conclusion=success`; all required MySQL integration and migration execution gates passed, including the WF-08A historical migration test. A subsequent documentation-only follow-up is at commit `18938d26f0b435336e6fafd90d8197806e32eca1`; its push and PR CI runs are being revalidated.
- Direct database integration coverage now rejects a forged SHIFT snapshot rate and direct HOUR/QUANTITY correction inserts. The migration test also preserves legacy correction rows across V47–V50 and verifies all three expected triggers are installed.
- PR #41 remains open and unmerged for review. No merge, `main` change, WF-08B start, or production migration is authorized by this phase.
- Remaining approval gate: complete a focused review of V46/V48/V49/V50 trigger interaction and authorization boundaries; CI success is necessary but is not itself approval.

## Implementation sequence and release gate

- First add the snapshot columns and map them in `ShiftCorrection`.
- The current constructor captures old/new SHIFT snapshots; measured-basis correction input and posting remain disabled.
- V48–V50 add snapshot validation, immutability and completed-shift compensation protection without editing V1–V46.
- Before approval, add/confirm migration tests against a clean database and an upgraded V46 database for trigger creation, historical row compatibility, malformed snapshot insertion, tenant isolation and zero-wage behavior.
- Do not remove the service's HOUR/QUANTITY rejection in WF-08A. Enable those bases only in WF-08B after journal/ledger correction tests pass.
- No production migration or merge is authorized by this phase.
