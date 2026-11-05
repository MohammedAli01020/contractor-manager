-- Keep the new compensation snapshot columns tied to the locked shift at creation,
-- and immutable for the lifetime of the audit record. Measured-basis corrections remain
-- disabled until WF-08B supplies a separately authorized new measurement/calculation path.
DROP TRIGGER IF EXISTS trg_shift_corrections_compensation_snapshot_insert;

CREATE TRIGGER trg_shift_corrections_compensation_snapshot_insert
BEFORE INSERT ON shift_corrections
FOR EACH ROW
BEGIN
    DECLARE matching_shifts INT DEFAULT 0;

    SELECT COUNT(*)
      INTO matching_shifts
      FROM shifts s
     WHERE s.tenant_id = NEW.tenant_id
       AND s.id = NEW.shift_id
       AND NEW.old_compensation_basis <=> s.compensation_basis
       AND NEW.new_compensation_basis <=> s.compensation_basis
       AND NEW.old_compensation_rate <=> s.compensation_rate
       AND NEW.new_compensation_rate <=> s.compensation_rate
       AND NEW.old_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.new_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.old_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.new_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.old_compensation_calculated_amount <=> s.compensation_calculated_amount
       AND NEW.new_compensation_calculated_amount <=> s.compensation_calculated_amount
       AND NEW.old_actual_hours <=> s.actual_hours
       AND NEW.new_actual_hours <=> s.actual_hours
       AND NEW.old_actual_quantity <=> s.actual_quantity
       AND NEW.new_actual_quantity <=> s.actual_quantity;

    IF matching_shifts <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SHIFT_CORRECTION_COMPENSATION_SNAPSHOT_MISMATCH';
    END IF;
END;

DROP TRIGGER IF EXISTS trg_shift_corrections_compensation_snapshot_update;

CREATE TRIGGER trg_shift_corrections_compensation_snapshot_update
BEFORE UPDATE ON shift_corrections
FOR EACH ROW
BEGIN
    IF NOT (NEW.old_compensation_basis <=> OLD.old_compensation_basis)
       OR NOT (NEW.new_compensation_basis <=> OLD.new_compensation_basis)
       OR NOT (NEW.old_compensation_rate <=> OLD.old_compensation_rate)
       OR NOT (NEW.new_compensation_rate <=> OLD.new_compensation_rate)
       OR NOT (NEW.old_compensation_unit_code <=> OLD.old_compensation_unit_code)
       OR NOT (NEW.new_compensation_unit_code <=> OLD.new_compensation_unit_code)
       OR NOT (NEW.old_compensation_currency_code <=> OLD.old_compensation_currency_code)
       OR NOT (NEW.new_compensation_currency_code <=> OLD.new_compensation_currency_code)
       OR NOT (NEW.old_compensation_calculated_amount <=> OLD.old_compensation_calculated_amount)
       OR NOT (NEW.new_compensation_calculated_amount <=> OLD.new_compensation_calculated_amount)
       OR NOT (NEW.old_actual_hours <=> OLD.old_actual_hours)
       OR NOT (NEW.new_actual_hours <=> OLD.new_actual_hours)
       OR NOT (NEW.old_actual_quantity <=> OLD.old_actual_quantity)
       OR NOT (NEW.new_actual_quantity <=> OLD.new_actual_quantity) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SHIFT_CORRECTION_COMPENSATION_SNAPSHOT_IMMUTABLE';
    END IF;
END;
