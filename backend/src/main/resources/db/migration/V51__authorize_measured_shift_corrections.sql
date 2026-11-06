-- WF-08B: measured-basis completed-shift correction authorization.
-- Forward-only: V44-V50 remain immutable. This migration replaces only the
-- snapshot-insert and completed-shift-compensation guards with measured-aware rules.
-- WF-08B: authorize measured-basis correction snapshots without changing the
-- historical compensation model, rate, unit, currency, wage source or agreement.
-- SHIFT corrections retain the original WF-08A legacy/agreement semantics.
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
       AND s.status = 'COMPLETED'
       AND s.finished_at IS NOT NULL
       AND NEW.old_compensation_basis <=> s.compensation_basis
       AND NEW.new_compensation_basis <=> s.compensation_basis
       AND NEW.old_compensation_rate <=> s.compensation_rate
       AND NEW.old_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.new_compensation_unit_code <=> s.compensation_unit_code
       AND NEW.old_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.new_compensation_currency_code <=> s.compensation_currency_code
       AND NEW.old_compensation_calculated_amount <=> s.compensation_calculated_amount
       AND NEW.old_actual_hours <=> s.actual_hours
       AND NEW.old_actual_quantity <=> s.actual_quantity
       AND NEW.new_compensation_calculated_amount <=> NEW.new_driver_wage
       AND (
           (s.compensation_basis = 'SHIFT'
            AND NEW.new_actual_hours <=> s.actual_hours
            AND NEW.new_actual_quantity <=> s.actual_quantity
            AND NEW.new_compensation_rate <=> (
                CASE WHEN s.wage_source = 'LEGACY_DRIVER_RATE'
                     THEN NEW.new_driver_wage ELSE s.compensation_rate END
            ))
           OR
           (s.compensation_basis = 'HOUR'
            AND s.actual_hours > 0
            AND s.actual_quantity IS NULL
            AND NEW.new_actual_hours > 0
            AND NEW.new_actual_quantity IS NULL
            AND NEW.new_compensation_rate <=> s.compensation_rate
            AND NEW.new_driver_wage <=> ROUND(s.compensation_rate * NEW.new_actual_hours, 2))
           OR
           (s.compensation_basis = 'QUANTITY'
            AND s.actual_quantity > 0
            AND s.actual_hours IS NULL
            AND s.compensation_unit_code IS NOT NULL
            AND TRIM(s.compensation_unit_code) <> ''
            AND NEW.new_actual_quantity > 0
            AND NEW.new_actual_hours IS NULL
            AND NEW.new_compensation_rate <=> s.compensation_rate
            AND NEW.new_driver_wage <=> ROUND(s.compensation_rate * NEW.new_actual_quantity, 2))
       );

    IF matching_shifts <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SHIFT_CORRECTION_COMPENSATION_SNAPSHOT_MISMATCH';
    END IF;
END;

-- WF-08B: permit only a measured-value correction authorized by the exact
-- APPLYING snapshot; all contractual identity and unit-rate fields remain frozen.
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
           AND c.new_compensation_unit_code <=> OLD.compensation_unit_code
           AND c.old_compensation_currency_code <=> OLD.compensation_currency_code
           AND c.new_compensation_currency_code <=> OLD.compensation_currency_code
           AND c.old_compensation_calculated_amount <=> OLD.compensation_calculated_amount
           AND c.new_compensation_calculated_amount <=> NEW.compensation_calculated_amount
           AND c.new_compensation_calculated_amount <=> NEW.driver_wage
           AND c.old_actual_hours <=> OLD.actual_hours
           AND c.new_actual_hours <=> NEW.actual_hours
           AND c.old_actual_quantity <=> OLD.actual_quantity
           AND c.new_actual_quantity <=> NEW.actual_quantity
           AND NEW.wage_source <=> OLD.wage_source
           AND NEW.compensation_agreement_id <=> OLD.compensation_agreement_id
           AND NEW.compensation_component_id <=> OLD.compensation_component_id
           AND NEW.compensation_agreement_reference <=> OLD.compensation_agreement_reference
           AND NEW.compensation_component_code <=> OLD.compensation_component_code
           AND NEW.compensation_component_name <=> OLD.compensation_component_name
           AND (
               (OLD.compensation_basis = 'SHIFT'
                AND NEW.actual_hours <=> OLD.actual_hours
                AND NEW.actual_quantity <=> OLD.actual_quantity
                AND NEW.compensation_rate <=> (
                    CASE WHEN OLD.wage_source = 'LEGACY_DRIVER_RATE'
                         THEN NEW.driver_wage ELSE OLD.compensation_rate END
                )
                AND c.new_compensation_rate <=> NEW.compensation_rate)
               OR
               (OLD.compensation_basis = 'HOUR'
                AND OLD.actual_hours > 0
                AND OLD.actual_quantity IS NULL
                AND NEW.actual_hours > 0
                AND NEW.actual_quantity IS NULL
                AND NEW.compensation_rate <=> OLD.compensation_rate
                AND NEW.compensation_calculated_amount <=> ROUND(OLD.compensation_rate * NEW.actual_hours, 2))
               OR
               (OLD.compensation_basis = 'QUANTITY'
                AND OLD.actual_quantity > 0
                AND OLD.actual_hours IS NULL
                AND NEW.actual_quantity > 0
                AND NEW.actual_hours IS NULL
                AND NEW.compensation_rate <=> OLD.compensation_rate
                AND NEW.compensation_calculated_amount <=> ROUND(OLD.compensation_rate * NEW.actual_quantity, 2))
           );

        IF matching_corrections <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_COMPENSATION_IMMUTABLE';
        END IF;
    END IF;
END;
