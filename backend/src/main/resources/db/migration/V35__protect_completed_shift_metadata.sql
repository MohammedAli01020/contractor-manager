-- Close completed-shift metadata gaps left by the correction-aware guard:
-- notes and tenant/id identity must not be changed outside an audited correction.
-- The V29 guard already validates notes when another protected correction field changes;
-- this trigger rejects notes-only edits and any tenant/primary-key reassignment.
DROP TRIGGER IF EXISTS trg_shifts_guard_completed_metadata;

CREATE TRIGGER trg_shifts_guard_completed_metadata
BEFORE UPDATE ON shifts
FOR EACH ROW
BEGIN
    IF OLD.status = 'COMPLETED' THEN
        IF NOT (NEW.id <=> OLD.id)
           OR NOT (NEW.tenant_id <=> OLD.tenant_id) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_METADATA_IMMUTABLE';
        END IF;

        IF NOT (NEW.notes <=> OLD.notes)
           AND NEW.status <=> OLD.status
           AND NEW.shift_date <=> OLD.shift_date
           AND NEW.shift_type <=> OLD.shift_type
           AND NEW.site_id <=> OLD.site_id
           AND NEW.driver_id <=> OLD.driver_id
           AND NEW.equipment_id <=> OLD.equipment_id
           AND NEW.driver_wage <=> OLD.driver_wage
           AND NEW.price <=> OLD.price
           AND NEW.equipment_operating_cost <=> OLD.equipment_operating_cost
           AND NEW.started_at <=> OLD.started_at
           AND NEW.finished_at <=> OLD.finished_at
           AND NEW.driver_wage_journal_entry_id <=> OLD.driver_wage_journal_entry_id
           AND NEW.cancel_reason <=> OLD.cancel_reason THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_METADATA_IMMUTABLE';
        END IF;
    END IF;
END;
