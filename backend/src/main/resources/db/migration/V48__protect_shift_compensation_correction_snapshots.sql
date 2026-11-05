-- Keep old compensation inputs tied to the locked shift at correction creation.
-- The new snapshot records the approved SHIFT-basis wage override independently:
--   * calculated amount follows the corrected driver wage;
--   * legacy driver-rate snapshots also move their rate with the wage;
--   * agreement-backed rates remain frozen and are never rewritten by a wage override.
-- HOUR/QUANTITY corrections remain disabled until WF-08B authorizes new measurements.
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
       -- The before snapshot must be an exact copy of the locked shift.
       AND NEW.old_compensation_basis <=> s.compensation_basis
       AND NEW.old_compensation_rate <=> s.compensation_rate
       AND NEW.old_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.old_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.old_compensation_calculated_amount <=> s.compensation_calculated_amount
       AND NEW.old_actual_hours <=> s.actual_hours
       AND NEW.old_actual_quantity <=> s.actual_quantity
       -- The correction does not change the compensation model or measured inputs.
       AND NEW.new_compensation_basis <=> s.compensation_basis
       AND NEW.new_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.new_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.new_actual_hours <=> s.actual_hours
       AND NEW.new_actual_quantity <=> s.actual_quantity
       -- A wage override changes the calculated amount. Only legacy driver-rate
       -- snapshots also change their rate; agreement rates remain historically frozen.
       AND NEW.new_compensation_calculated_amount <=> NEW.new_driver_wage
       AND NEW.new_compensation_rate <=> (
           CASE
               WHEN s.wage_source = 'LEGACY_DRIVER_RATE' THEN NEW.new_driver_wage
               ELSE s.compensation_rate
           END
       );

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
