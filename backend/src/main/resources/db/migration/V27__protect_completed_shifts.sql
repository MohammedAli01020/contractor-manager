-- Completed shifts are finalized operational records even when their wage is zero
-- and therefore no wage journal / worker-ledger earning was created.
-- Notes remain editable; corrections to source fields require a dedicated audited workflow.

CREATE TRIGGER trg_shifts_protect_completed
BEFORE UPDATE ON shifts
FOR EACH ROW
BEGIN
    DECLARE matching_wage_journals INT DEFAULT 0;

    -- Validate every non-null link, not only links used during positive-wage
    -- completion. This also prevents attaching an unrelated journal to a
    -- zero-wage shift or to a still-running shift for later misuse.
    IF NEW.driver_wage_journal_entry_id IS NOT NULL THEN
        SELECT COUNT(*)
          INTO matching_wage_journals
          FROM journal_entries
         WHERE id = NEW.driver_wage_journal_entry_id
           AND tenant_id = NEW.tenant_id
           AND source_type = 'SHIFT_WAGE'
           AND source_id = NEW.id
           AND posting_key = CONCAT('shift-wage-', NEW.id)
           AND status = 'POSTED';

        IF matching_wage_journals <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_JOURNAL_MISMATCH';
        END IF;
    END IF;

    IF OLD.status = 'IN_PROGRESS'
       AND NEW.status = 'COMPLETED'
       AND NEW.driver_wage > 0
       AND NEW.driver_wage_journal_entry_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_JOURNAL_REQUIRED';
    END IF;

    IF OLD.status = 'COMPLETED'
       AND (
           NOT (NEW.status <=> OLD.status)
           OR NOT (NEW.shift_date <=> OLD.shift_date)
           OR NOT (NEW.shift_type <=> OLD.shift_type)
           OR NOT (NEW.site_id <=> OLD.site_id)
           OR NOT (NEW.driver_id <=> OLD.driver_id)
           OR NOT (NEW.equipment_id <=> OLD.equipment_id)
           OR NOT (NEW.driver_wage <=> OLD.driver_wage)
           OR NOT (NEW.price <=> OLD.price)
           OR NOT (NEW.equipment_operating_cost <=> OLD.equipment_operating_cost)
           OR NOT (NEW.started_at <=> OLD.started_at)
           OR NOT (NEW.finished_at <=> OLD.finished_at)
           OR NOT (NEW.driver_wage_journal_entry_id <=> OLD.driver_wage_journal_entry_id)
           OR NOT (NEW.cancel_reason <=> OLD.cancel_reason)
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'COMPLETED_SHIFT_IMMUTABLE';
    END IF;
END;

CREATE TRIGGER trg_shifts_prevent_delete_completed
BEFORE DELETE ON shifts
FOR EACH ROW
BEGIN
    IF OLD.status = 'COMPLETED' OR OLD.driver_wage_journal_entry_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'COMPLETED_SHIFT_CANNOT_BE_DELETED';
    END IF;
END;
