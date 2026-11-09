-- Stage 2 preparation: allow future Workforce-native movements to have no
-- legacy Driver source. Mirrored rows continue to carry both provenance values.
-- This migration does NOT change the active writer or disable the V56 trigger.

ALTER TABLE workforce_ledger_entries
    MODIFY COLUMN legacy_source_entry_id BIGINT NULL,
    MODIFY COLUMN legacy_driver_id BIGINT NULL,
    ADD CONSTRAINT chk_workforce_ledger_legacy_provenance_pair
        CHECK (
            (legacy_source_entry_id IS NULL AND legacy_driver_id IS NULL)
            OR
            (legacy_source_entry_id IS NOT NULL AND legacy_driver_id IS NOT NULL)
        );
