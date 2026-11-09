-- Add canonical Workforce ledger references to the audited correction record.
-- The legacy ID columns remain unchanged compatibility/provenance references.
-- Historical APPLIED correction rows are intentionally not updated: V34 makes
-- them immutable. Runtime correction code can resolve their mirror through the
-- existing legacy source reference while all newly-created corrections persist
-- the canonical Workforce IDs.

ALTER TABLE workforce_ledger_entries
    ADD UNIQUE KEY uq_workforce_ledger_id_tenant (id, tenant_id);

ALTER TABLE shift_corrections
    ADD COLUMN old_earning_workforce_ledger_entry_id BIGINT NULL,
    ADD COLUMN reversed_earning_workforce_ledger_entry_id BIGINT NULL,
    ADD COLUMN replacement_earning_workforce_ledger_entry_id BIGINT NULL,
    ADD KEY ix_shift_corrections_old_workforce_ledger (
        tenant_id, old_earning_workforce_ledger_entry_id
    ),
    ADD KEY ix_shift_corrections_reversed_workforce_ledger (
        tenant_id, reversed_earning_workforce_ledger_entry_id
    ),
    ADD KEY ix_shift_corrections_replacement_workforce_ledger (
        tenant_id, replacement_earning_workforce_ledger_entry_id
    ),
    ADD CONSTRAINT fk_shift_correction_old_workforce_ledger
        FOREIGN KEY (old_earning_workforce_ledger_entry_id, tenant_id)
        REFERENCES workforce_ledger_entries (id, tenant_id),
    ADD CONSTRAINT fk_shift_correction_reversed_workforce_ledger
        FOREIGN KEY (reversed_earning_workforce_ledger_entry_id, tenant_id)
        REFERENCES workforce_ledger_entries (id, tenant_id),
    ADD CONSTRAINT fk_shift_correction_replacement_workforce_ledger
        FOREIGN KEY (replacement_earning_workforce_ledger_entry_id, tenant_id)
        REFERENCES workforce_ledger_entries (id, tenant_id);
