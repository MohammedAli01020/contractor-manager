-- Require the wage snapshot and its matching journal whenever a shift enters COMPLETED,
-- regardless of its previous status. This closes the legacy CANCELLED -> COMPLETED path.
DROP TRIGGER IF EXISTS trg_shifts_protect_completed;

CREATE TRIGGER trg_shifts_protect_completed
BEFORE UPDATE ON shifts
FOR EACH ROW
BEGIN
    DECLARE matching_wage_journals INT DEFAULT 0;

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

    IF NEW.status = 'COMPLETED'
       AND OLD.status <> 'COMPLETED' THEN
        IF NEW.driver_wage IS NULL OR NEW.driver_wage <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_REQUIRED';
        END IF;

        IF NEW.driver_wage_journal_entry_id IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_JOURNAL_REQUIRED';
        END IF;
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
