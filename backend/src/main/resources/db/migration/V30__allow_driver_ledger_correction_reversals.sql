-- Allow the audited reversal movement created by completed-shift corrections.
-- Preserve all existing transaction types and the recoverable-advance zero-effect rule.
-- Replace the type check in one ALTER TABLE operation so the constraint is never
-- intentionally left absent between separate DDL statements.
ALTER TABLE driver_ledger_entries
    DROP CHECK chk_driver_ledger_type,
    ADD CONSTRAINT chk_driver_ledger_type CHECK (
        transaction_type IN (
            'EARNING',
            'PAYMENT',
            'ADVANCE',
            'RECOVERABLE_ADVANCE',
            'ADJUSTMENT',
            'ADVANCE_RECOVERY',
            'CORRECTION_REVERSAL'
        )
    );
