-- Preserve the one-original-earning-per-shift invariant while allowing the
-- explicitly keyed replacement EARNING created by the audited correction workflow.
-- Correction replacement keys are generated server-side as shift-wage-correction-<id>.
ALTER TABLE driver_ledger_entries
    DROP INDEX uq_driver_ledger_one_shift_earning,
    DROP COLUMN earning_shift_id,
    ADD COLUMN earning_shift_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN transaction_type = 'EARNING'
                 AND client_request_key NOT LIKE 'shift-wage-correction-%'
                THEN shift_id
                ELSE NULL
            END
        ) STORED,
    ADD UNIQUE KEY uq_driver_ledger_one_shift_earning (tenant_id, earning_shift_id);
