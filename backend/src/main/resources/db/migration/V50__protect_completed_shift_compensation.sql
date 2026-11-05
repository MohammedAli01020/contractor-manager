-- Completed-shift compensation metadata is immutable outside an audited correction.
-- The approved SHIFT correction may update only the rate/calculated amount represented
-- by the immutable old/new snapshot row. Contract identity and measured values stay frozen.
DROP TRIGGER IF EXISTS trg_shifts_protect_completed_compensation;

CREATE TRIGGER trg_shifts_protect_completed_compensation
BEFORE UPDATE ON shifts
FOR EACH ROW
BEGIN
    DECLARE matching_corrections INT DEFAULT 0;

    IF OLD.status = 'COMPLETED'
       AND (
           NOT (NEW.wage_source <=> OLD.wage_source)
           OR NOT (NEW.compensation_agreement_id <=> OLD.compensation_agreement_id)
           OR NOT (NEW.compensation_component_id <=> OLD.compensation_component_id)
           OR NOT (NEW.compensation_agreement_reference <=> OLD.compensation_agreement_reference)
           OR NOT (NEW.compensation_component_code <=> OLD.compensation_component_code)
           OR NOT (NEW.compensation_component_name <=> OLD.compensation_component_name)
           OR NOT (NEW.compensation_basis <=> OLD.compensation_basis)
           OR NOT (NEW.compensation_currency_code <=> OLD.compensation_currency_code)
           OR NOT (NEW.compensation_unit_code <=> OLD.compensation_unit_code)
           OR NOT (NEW.compensation_rate <=> OLD.compensation_rate)
           OR NOT (NEW.compensation_calculated_amount <=> OLD.compensation_calculated_amount)
           OR NOT (NEW.actual_hours <=> OLD.actual_hours)
           OR NOT (NEW.actual_quantity <=> OLD.actual_quantity)
       ) THEN
        SELECT COUNT(*)
          INTO matching_corrections
          FROM shift_corrections c
         WHERE c.tenant_id = OLD.tenant_id
           AND c.shift_id = OLD.id
           AND c.status = 'APPLYING'
           AND c.expected_shift_version = OLD.version
           AND c.old_compensation_basis <=> OLD.compensation_basis
           AND c.new_compensation_basis <=> NEW.compensation_basis
           AND c.old_compensation_rate <=> OLD.compensation_rate
           AND c.new_compensation_rate <=> NEW.compensation_rate
           AND c.old_compensation_unit_code <=> OLD.compensation_unit_code
           AND c.new_compensation_unit_code <=> NEW.compensation_unit_code
           AND c.old_compensation_currency_code <=> OLD.compensation_currency_code
           AND c.new_compensation_currency_code <=> NEW.compensation_currency_code
           AND c.old_compensation_calculated_amount <=> OLD.compensation_calculated_amount
           AND c.new_compensation_calculated_amount <=> NEW.compensation_calculated_amount
           AND c.old_actual_hours <=> OLD.actual_hours
           AND c.new_actual_hours <=> NEW.actual_hours
           AND c.old_actual_quantity <=> OLD.actual_quantity
           AND c.new_actual_quantity <=> NEW.actual_quantity
           AND NEW.wage_source <=> OLD.wage_source
           AND NEW.compensation_agreement_id <=> OLD.compensation_agreement_id
           AND NEW.compensation_component_id <=> OLD.compensation_component_id
           AND NEW.compensation_agreement_reference <=> OLD.compensation_agreement_reference
           AND NEW.compensation_component_code <=> OLD.compensation_component_code
           AND NEW.compensation_component_name <=> OLD.compensation_component_name;

        IF matching_corrections <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_COMPENSATION_IMMUTABLE';
        END IF;
    END IF;
END;
