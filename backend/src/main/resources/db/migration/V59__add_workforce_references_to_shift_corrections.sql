-- Add canonical Workforce references beside the legacy correction references.
-- Existing legacy IDs remain unchanged; each new ID is resolved by tenant + provenance.
-- This is a compatibility-stage migration: legacy writers and V29/V35 triggers remain active.

ALTER TABLE workforce_ledger_entries
    ADD UNIQUE KEY uq_workforce_ledger_tenant_id (tenant_id, id);

ALTER TABLE shift_corrections
    ADD COLUMN old_earning_workforce_ledger_entry_id BIGINT NULL,
    ADD COLUMN reversed_earning_workforce_ledger_entry_id BIGINT NULL,
    ADD COLUMN replacement_earning_workforce_ledger_entry_id BIGINT NULL;

UPDATE shift_corrections c
LEFT JOIN workforce_ledger_entries old_entry
  ON old_entry.tenant_id = c.tenant_id
 AND old_entry.legacy_source_entry_id = c.old_earning_ledger_entry_id
LEFT JOIN workforce_ledger_entries reversed_entry
  ON reversed_entry.tenant_id = c.tenant_id
 AND reversed_entry.legacy_source_entry_id = c.reversed_earning_ledger_entry_id
LEFT JOIN workforce_ledger_entries replacement_entry
  ON replacement_entry.tenant_id = c.tenant_id
 AND replacement_entry.legacy_source_entry_id = c.replacement_earning_ledger_entry_id
SET c.old_earning_workforce_ledger_entry_id = old_entry.id,
    c.reversed_earning_workforce_ledger_entry_id = reversed_entry.id,
    c.replacement_earning_workforce_ledger_entry_id = replacement_entry.id;

ALTER TABLE shift_corrections
    ADD KEY ix_shift_corrections_old_workforce_ledger
        (tenant_id, old_earning_workforce_ledger_entry_id),
    ADD KEY ix_shift_corrections_reversed_workforce_ledger
        (tenant_id, reversed_earning_workforce_ledger_entry_id),
    ADD KEY ix_shift_corrections_replacement_workforce_ledger
        (tenant_id, replacement_earning_workforce_ledger_entry_id),
    ADD CONSTRAINT fk_shift_correction_old_workforce_ledger
        FOREIGN KEY (tenant_id, old_earning_workforce_ledger_entry_id)
        REFERENCES workforce_ledger_entries (tenant_id, id),
    ADD CONSTRAINT fk_shift_correction_reversed_workforce_ledger
        FOREIGN KEY (tenant_id, reversed_earning_workforce_ledger_entry_id)
        REFERENCES workforce_ledger_entries (tenant_id, id),
    ADD CONSTRAINT fk_shift_correction_replacement_workforce_ledger
        FOREIGN KEY (tenant_id, replacement_earning_workforce_ledger_entry_id)
        REFERENCES workforce_ledger_entries (tenant_id, id),
    ADD CONSTRAINT chk_shift_correction_old_ledger_ref_pair CHECK (
        (old_earning_ledger_entry_id IS NULL AND old_earning_workforce_ledger_entry_id IS NULL)
        OR (old_earning_ledger_entry_id IS NOT NULL AND old_earning_workforce_ledger_entry_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_shift_correction_reversed_ledger_ref_pair CHECK (
        (reversed_earning_ledger_entry_id IS NULL AND reversed_earning_workforce_ledger_entry_id IS NULL)
        OR (reversed_earning_ledger_entry_id IS NOT NULL AND reversed_earning_workforce_ledger_entry_id IS NOT NULL)
    ),
    ADD CONSTRAINT chk_shift_correction_replacement_ledger_ref_pair CHECK (
        (replacement_earning_ledger_entry_id IS NULL AND replacement_earning_workforce_ledger_entry_id IS NULL)
        OR (replacement_earning_ledger_entry_id IS NOT NULL AND replacement_earning_workforce_ledger_entry_id IS NOT NULL)
    );

-- The existing V44 guard protects legacy references. This companion guard validates
-- the canonical Workforce references before APPLYING can become APPLIED.
DROP TRIGGER IF EXISTS trg_shift_corrections_workforce_refs_guard;

CREATE TRIGGER trg_shift_corrections_workforce_refs_guard
BEFORE UPDATE ON shift_corrections
FOR EACH ROW
BEGIN
    IF OLD.status = 'APPLYING' AND NEW.status = 'APPLIED' THEN
        IF NEW.old_driver_wage = 0 THEN
            IF NEW.old_earning_workforce_ledger_entry_id IS NOT NULL
               OR NEW.reversed_earning_workforce_ledger_entry_id IS NOT NULL THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'SHIFT_CORRECTION_WORKFORCE_OLD_REFERENCES_INVALID';
            END IF;
        ELSE
            IF NOT EXISTS (
                SELECT 1
                  FROM workforce_ledger_entries w
                  JOIN shifts s
                    ON s.id = NEW.shift_id
                   AND s.tenant_id = NEW.tenant_id
                 WHERE w.id = NEW.old_earning_workforce_ledger_entry_id
                   AND w.tenant_id = NEW.tenant_id
                   AND w.legacy_source_entry_id = NEW.old_earning_ledger_entry_id
                   AND w.legacy_driver_id = NEW.old_driver_id
                   AND w.workforce_member_id = s.workforce_member_id
                   AND w.shift_id = NEW.shift_id
                   AND w.transaction_type = 'EARNING'
                   AND w.journal_entry_id = NEW.old_wage_journal_entry_id
                   AND w.amount = NEW.old_driver_wage
                   AND w.balance_effect = NEW.old_driver_wage
            ) OR NOT EXISTS (
                SELECT 1
                  FROM workforce_ledger_entries w
                  JOIN shifts s
                    ON s.id = NEW.shift_id
                   AND s.tenant_id = NEW.tenant_id
                 WHERE w.id = NEW.reversed_earning_workforce_ledger_entry_id
                   AND w.tenant_id = NEW.tenant_id
                   AND w.legacy_source_entry_id = NEW.reversed_earning_ledger_entry_id
                   AND w.legacy_driver_id = NEW.old_driver_id
                   AND w.workforce_member_id = s.workforce_member_id
                   AND w.shift_id = NEW.shift_id
                   AND w.transaction_type = 'CORRECTION_REVERSAL'
                   AND w.journal_entry_id = NEW.wage_reversal_journal_entry_id
                   AND w.amount = NEW.old_driver_wage
                   AND w.balance_effect = -NEW.old_driver_wage
            ) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'SHIFT_CORRECTION_WORKFORCE_OLD_REFERENCES_INVALID';
            END IF;
        END IF;

        IF NEW.new_driver_wage = 0 THEN
            IF NEW.replacement_earning_workforce_ledger_entry_id IS NOT NULL THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'SHIFT_CORRECTION_WORKFORCE_REPLACEMENT_INVALID';
            END IF;
        ELSE
            IF NOT EXISTS (
                SELECT 1
                  FROM workforce_ledger_entries w
                  JOIN shifts s
                    ON s.id = NEW.shift_id
                   AND s.tenant_id = NEW.tenant_id
                 WHERE w.id = NEW.replacement_earning_workforce_ledger_entry_id
                   AND w.tenant_id = NEW.tenant_id
                   AND w.legacy_source_entry_id = NEW.replacement_earning_ledger_entry_id
                   AND w.legacy_driver_id = NEW.new_driver_id
                   AND w.workforce_member_id = s.workforce_member_id
                   AND w.shift_id = NEW.shift_id
                   AND w.transaction_type = 'EARNING'
                   AND w.journal_entry_id = NEW.replacement_wage_journal_entry_id
                   AND w.amount = NEW.new_driver_wage
                   AND w.balance_effect = NEW.new_driver_wage
            ) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'SHIFT_CORRECTION_WORKFORCE_REPLACEMENT_INVALID';
            END IF;
        END IF;
    END IF;
END;
