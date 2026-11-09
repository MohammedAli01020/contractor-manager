-- Add canonical Workforce references beside the legacy correction references.
-- Existing APPLIED correction audit rows are immutable: do not rewrite/backfill them.
-- New corrections populate the canonical references while legacy writers/triggers remain active.

ALTER TABLE workforce_ledger_entries
    ADD UNIQUE KEY uq_workforce_ledger_tenant_id (tenant_id, id);

ALTER TABLE shift_corrections
    ADD COLUMN old_earning_workforce_ledger_entry_id BIGINT NULL,
    ADD COLUMN reversed_earning_workforce_ledger_entry_id BIGINT NULL,
    ADD COLUMN replacement_earning_workforce_ledger_entry_id BIGINT NULL,
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
        REFERENCES workforce_ledger_entries (tenant_id, id);

-- A newly created correction must point its original earning reference at the
-- exact Workforce mirror of the legacy earning. Historical rows are not rewritten.
DROP TRIGGER IF EXISTS trg_shift_corrections_workforce_refs_insert;

CREATE TRIGGER trg_shift_corrections_workforce_refs_insert
BEFORE INSERT ON shift_corrections
FOR EACH ROW
BEGIN
    IF NEW.old_driver_wage = 0 THEN
        IF NEW.old_earning_ledger_entry_id IS NOT NULL
           OR NEW.old_earning_workforce_ledger_entry_id IS NOT NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'SHIFT_CORRECTION_WORKFORCE_OLD_REFERENCES_INVALID';
        END IF;
    ELSE
        IF NOT EXISTS (
            SELECT 1
              FROM workforce_ledger_entries w
              JOIN drivers d
                ON d.id = NEW.old_driver_id
               AND d.tenant_id = NEW.tenant_id
             WHERE w.id = NEW.old_earning_workforce_ledger_entry_id
               AND w.tenant_id = NEW.tenant_id
               AND w.legacy_source_entry_id = NEW.old_earning_ledger_entry_id
               AND w.legacy_driver_id = NEW.old_driver_id
               AND w.workforce_member_id = d.workforce_member_id
               AND w.shift_id = NEW.shift_id
               AND w.transaction_type = 'EARNING'
               AND w.journal_entry_id = NEW.old_wage_journal_entry_id
               AND w.amount = NEW.old_driver_wage
               AND w.balance_effect = NEW.old_driver_wage
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'SHIFT_CORRECTION_WORKFORCE_OLD_REFERENCES_INVALID';
        END IF;
    END IF;
END;

-- Validate the complete canonical correction chain before APPLYING can become APPLIED.
-- The V44 guard still protects the legacy references and all APPLIED rows remain immutable.
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
                  JOIN drivers d
                    ON d.id = NEW.old_driver_id
                   AND d.tenant_id = NEW.tenant_id
                 WHERE w.id = NEW.old_earning_workforce_ledger_entry_id
                   AND w.tenant_id = NEW.tenant_id
                   AND w.legacy_source_entry_id = NEW.old_earning_ledger_entry_id
                   AND w.legacy_driver_id = NEW.old_driver_id
                   AND w.workforce_member_id = d.workforce_member_id
                   AND w.shift_id = NEW.shift_id
                   AND w.transaction_type = 'EARNING'
                   AND w.journal_entry_id = NEW.old_wage_journal_entry_id
                   AND w.amount = NEW.old_driver_wage
                   AND w.balance_effect = NEW.old_driver_wage
            ) OR NOT EXISTS (
                SELECT 1
                  FROM workforce_ledger_entries w
                  JOIN drivers d
                    ON d.id = NEW.old_driver_id
                   AND d.tenant_id = NEW.tenant_id
                 WHERE w.id = NEW.reversed_earning_workforce_ledger_entry_id
                   AND w.tenant_id = NEW.tenant_id
                   AND w.legacy_source_entry_id = NEW.reversed_earning_ledger_entry_id
                   AND w.legacy_driver_id = NEW.old_driver_id
                   AND w.workforce_member_id = d.workforce_member_id
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
                  JOIN drivers d
                    ON d.id = NEW.new_driver_id
                   AND d.tenant_id = NEW.tenant_id
                 WHERE w.id = NEW.replacement_earning_workforce_ledger_entry_id
                   AND w.tenant_id = NEW.tenant_id
                   AND w.legacy_source_entry_id = NEW.replacement_earning_ledger_entry_id
                   AND w.legacy_driver_id = NEW.new_driver_id
                   AND w.workforce_member_id = d.workforce_member_id
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
