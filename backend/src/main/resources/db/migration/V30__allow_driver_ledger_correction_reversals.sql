-- Allow the audited reversal movement created by completed-shift corrections.
-- Preserve all existing transaction types and the recoverable-advance zero-effect rule.
ALTER TABLE driver_ledger_entries
    DROP CHECK chk_driver_ledger_type;

ALTER TABLE driver_ledger_entries
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
