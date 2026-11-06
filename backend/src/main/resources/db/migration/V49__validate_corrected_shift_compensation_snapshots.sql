-- WF-08A: validate before/after snapshots according to Shift.setDriverWage semantics.
-- The original frozen basis/unit/currency stay unchanged. The calculated amount follows
-- the corrected wage; only LEGACY_DRIVER_RATE changes its rate with the corrected wage.
-- HOUR and QUANTITY corrections remain disabled until WF-08B validates new measurements.
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
       AND s.compensation_basis NOT IN ('HOUR', 'QUANTITY')
       AND NEW.old_compensation_basis <=> s.compensation_basis
       AND NEW.new_compensation_basis <=> s.compensation_basis
       AND NEW.old_compensation_rate <=> s.compensation_rate
       AND NEW.new_compensation_rate <=> (
            CASE
                WHEN s.wage_source = 'LEGACY_DRIVER_RATE' THEN NEW.new_driver_wage
                ELSE s.compensation_rate
            END
       )
       AND NEW.old_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.new_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.old_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.new_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.old_compensation_calculated_amount <=> s.compensation_calculated_amount
       AND NEW.new_compensation_calculated_amount <=> NEW.new_driver_wage
       AND NEW.old_actual_hours <=> s.actual_hours
       AND NEW.new_actual_hours <=> s.actual_hours
       AND NEW.old_actual_quantity <=> s.actual_quantity
       AND NEW.new_actual_quantity <=> s.actual_quantity;

    IF matching_shifts <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SHIFT_CORRECTION_COMPENSATION_SNAPSHOT_MISMATCH';
    END IF;
END;
