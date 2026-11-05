-- WF-05 zero-wage behavior for audited completed SHIFT corrections.
-- No zero-value journal or driver-ledger entries are created.
-- Zero is a valid frozen wage value. Positive wages still require their posted wage journal.
-- A zero-wage shift must not carry a wage journal; it completes without wage/earning/recovery postings.
DROP TRIGGER IF EXISTS trg_shifts_protect_completed;

CREATE TRIGGER trg_shifts_protect_completed
BEFORE UPDATE ON shifts
FOR EACH ROW
BEGIN
    DECLARE matching_corrections INT DEFAULT 0;

    IF NEW.driver_wage_journal_entry_id IS NOT NULL THEN
        IF OLD.status = 'COMPLETED'
           AND NOT (NEW.driver_wage_journal_entry_id <=> OLD.driver_wage_journal_entry_id) THEN
            -- A completed shift may point to a replacement wage journal only when
            -- the APPLYING correction row records that exact posted journal.
            SELECT COUNT(*)
              INTO matching_corrections
              FROM shift_corrections c
              JOIN journal_entries j
                ON j.id = c.replacement_wage_journal_entry_id
               AND j.tenant_id = c.tenant_id
               AND j.source_type = 'SHIFT_WAGE_CORRECTION'
               AND j.source_id = c.id
               AND j.posting_key = CONCAT('shift-wage-correction-', c.id)
               AND j.status = 'POSTED'
             WHERE c.tenant_id = NEW.tenant_id
               AND c.shift_id = NEW.id
               AND c.status = 'APPLYING'
               AND (c.old_wage_journal_entry_id <=> OLD.driver_wage_journal_entry_id)
               AND (c.replacement_wage_journal_entry_id <=> NEW.driver_wage_journal_entry_id);

            IF matching_corrections <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_CORRECTION_JOURNAL_MISMATCH';
            END IF;
        ELSE
            SELECT COUNT(*)
              INTO matching_corrections
              FROM journal_entries j
             WHERE j.id = NEW.driver_wage_journal_entry_id
               AND j.tenant_id = NEW.tenant_id
               AND j.status = 'POSTED'
               AND (
                   (j.source_type = 'SHIFT_WAGE'
                    AND j.source_id = NEW.id
                    AND j.posting_key = CONCAT('shift-wage-', NEW.id))
                   OR EXISTS (
                       SELECT 1
                         FROM shift_corrections c
                        WHERE c.tenant_id = NEW.tenant_id
                          AND c.shift_id = NEW.id
                          AND c.status = 'APPLIED'
                          AND c.replacement_wage_journal_entry_id = NEW.driver_wage_journal_entry_id
                          AND j.source_type = 'SHIFT_WAGE_CORRECTION'
                          AND j.source_id = c.id
                          AND j.posting_key = CONCAT('shift-wage-correction-', c.id)
                   )
               );

            IF matching_corrections <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_JOURNAL_MISMATCH';
            END IF;
        END IF;
    END IF;

    IF NEW.status = 'COMPLETED'
       AND OLD.status <> 'COMPLETED' THEN
        IF NEW.driver_wage IS NULL OR NEW.driver_wage < 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_INVALID';
        END IF;
        IF NEW.driver_wage > 0 AND NEW.driver_wage_journal_entry_id IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_JOURNAL_REQUIRED';
        END IF;
        IF NEW.driver_wage = 0 AND NEW.driver_wage_journal_entry_id IS NOT NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'ZERO_WAGE_SHIFT_MUST_NOT_HAVE_WAGE_JOURNAL';
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
        SELECT COUNT(*)
          INTO matching_corrections
          FROM shift_corrections c
         WHERE c.tenant_id = OLD.tenant_id
           AND c.shift_id = OLD.id
           AND c.status = 'APPLYING'
           AND c.expected_shift_version = OLD.version
           AND c.old_shift_date = OLD.shift_date
           AND c.new_shift_date = NEW.shift_date
           AND c.old_shift_type = OLD.shift_type
           AND c.new_shift_type = NEW.shift_type
           AND c.old_site_id = OLD.site_id
           AND c.new_site_id = NEW.site_id
           AND c.old_driver_id = OLD.driver_id
           AND c.new_driver_id = NEW.driver_id
           AND c.new_driver_id = c.old_driver_id
           AND c.old_equipment_id = OLD.equipment_id
           AND c.new_equipment_id = NEW.equipment_id
           AND c.old_driver_wage = OLD.driver_wage
           AND c.new_driver_wage = NEW.driver_wage
           AND c.old_price = OLD.price
           AND c.new_price = NEW.price
           AND c.old_equipment_operating_cost = OLD.equipment_operating_cost
           AND c.new_equipment_operating_cost = NEW.equipment_operating_cost
           AND (c.old_notes <=> OLD.notes)
           AND (c.new_notes <=> NEW.notes)
           AND c.old_wage_journal_entry_id = OLD.driver_wage_journal_entry_id
           AND c.replacement_wage_journal_entry_id = NEW.driver_wage_journal_entry_id
           AND (
               (c.old_driver_wage = 0
                AND c.old_wage_journal_entry_id IS NULL
                AND c.wage_reversal_journal_entry_id IS NULL
                AND c.old_earning_ledger_entry_id IS NULL
                AND c.reversed_earning_ledger_entry_id IS NULL)
               OR
               (c.old_driver_wage > 0
                AND c.old_wage_journal_entry_id = OLD.driver_wage_journal_entry_id
                AND c.wage_reversal_journal_entry_id IS NOT NULL
                AND c.old_earning_ledger_entry_id IS NOT NULL
                AND c.reversed_earning_ledger_entry_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM journal_entries r
                     WHERE r.id = c.wage_reversal_journal_entry_id
                       AND r.tenant_id = c.tenant_id
                       AND r.source_type = 'SHIFT_WAGE_CORRECTION_REVERSAL'
                       AND r.source_id = c.id
                       AND r.posting_key = CONCAT('shift-wage-correction-reversal-', c.id)
                       AND r.reversal_of_entry_id = c.old_wage_journal_entry_id
                       AND r.status = 'POSTED')
                AND EXISTS (SELECT 1 FROM driver_ledger_entries old_earning
                     WHERE old_earning.id = c.old_earning_ledger_entry_id
                       AND old_earning.tenant_id = c.tenant_id
                       AND old_earning.driver_id = c.old_driver_id
                       AND old_earning.shift_id = c.shift_id
                       AND old_earning.transaction_type = 'EARNING'
                       AND old_earning.journal_entry_id = c.old_wage_journal_entry_id)
                AND EXISTS (SELECT 1 FROM driver_ledger_entries reversed_earning
                     WHERE reversed_earning.id = c.reversed_earning_ledger_entry_id
                       AND reversed_earning.tenant_id = c.tenant_id
                       AND reversed_earning.driver_id = c.old_driver_id
                       AND reversed_earning.shift_id = c.shift_id
                       AND reversed_earning.transaction_type = 'CORRECTION_REVERSAL'
                       AND reversed_earning.journal_entry_id = c.wage_reversal_journal_entry_id
                       AND reversed_earning.amount = c.old_driver_wage
                       AND reversed_earning.balance_effect = -c.old_driver_wage))
           )
           AND (
               (c.new_driver_wage = 0
                AND c.replacement_wage_journal_entry_id IS NULL
                AND c.replacement_earning_ledger_entry_id IS NULL)
               OR
               (c.new_driver_wage > 0
                AND c.replacement_wage_journal_entry_id = NEW.driver_wage_journal_entry_id
                AND c.replacement_earning_ledger_entry_id IS NOT NULL
                AND EXISTS (SELECT 1 FROM driver_ledger_entries replacement_earning
                     WHERE replacement_earning.id = c.replacement_earning_ledger_entry_id
                       AND replacement_earning.tenant_id = c.tenant_id
                       AND replacement_earning.driver_id = c.new_driver_id
                       AND replacement_earning.shift_id = c.shift_id
                       AND replacement_earning.transaction_type = 'EARNING'
                       AND replacement_earning.journal_entry_id = c.replacement_wage_journal_entry_id
                       AND replacement_earning.amount = c.new_driver_wage
                       AND replacement_earning.balance_effect = c.new_driver_wage))
           )
           -- A correction changes only the captured business/financial fields.
           -- Lifecycle timestamps, cancellation state and original journal linkage stay immutable.
           AND NEW.status = 'COMPLETED'
           AND NEW.driver_wage >= 0
           AND ((NEW.driver_wage = 0 AND NEW.driver_wage_journal_entry_id IS NULL)
                OR (NEW.driver_wage > 0 AND NEW.driver_wage_journal_entry_id IS NOT NULL))
           AND (NEW.started_at <=> OLD.started_at)
           AND (NEW.finished_at <=> OLD.finished_at)
           AND (NEW.cancel_reason <=> OLD.cancel_reason);

        IF matching_corrections <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_IMMUTABLE';
        END IF;
    END IF;
END;


-- Allow a replacement EARNING when the previous SHIFT wage was zero.
-- Positive old wages still require the exact reversal journal and ledger row.
DROP TRIGGER IF EXISTS trg_driver_ledger_validate_shift_correction;

CREATE TRIGGER trg_driver_ledger_validate_shift_correction
BEFORE INSERT ON driver_ledger_entries
FOR EACH ROW
BEGIN
    DECLARE matching_records INT DEFAULT 0;

    IF NEW.transaction_type = 'CORRECTION_REVERSAL' THEN
        SELECT COUNT(*)
          INTO matching_records
          FROM shift_corrections c
          JOIN journal_entries r
            ON r.id = NEW.journal_entry_id
           AND r.tenant_id = c.tenant_id
           AND r.source_type = 'SHIFT_WAGE_CORRECTION_REVERSAL'
           AND r.source_id = c.id
           AND r.posting_key = CONCAT('shift-wage-correction-reversal-', c.id)
           AND r.reversal_of_entry_id = c.old_wage_journal_entry_id
           AND r.status = 'POSTED'
          JOIN driver_ledger_entries old_earning
            ON old_earning.id = c.old_earning_ledger_entry_id
           AND old_earning.tenant_id = c.tenant_id
           AND old_earning.driver_id = c.old_driver_id
           AND old_earning.shift_id = c.shift_id
           AND old_earning.transaction_type = 'EARNING'
           AND old_earning.journal_entry_id = c.old_wage_journal_entry_id
         WHERE c.tenant_id = NEW.tenant_id
           AND c.shift_id = NEW.shift_id
           AND c.status = 'APPLYING'
           AND NEW.client_request_key = CONCAT('shift-wage-correction-reversal-', c.id)
           AND NEW.driver_id = c.old_driver_id
           AND NEW.transaction_date = c.old_shift_date
           AND NEW.amount = c.old_driver_wage
           AND NEW.balance_effect = -c.old_driver_wage;

        IF matching_records <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'UNAUTHORIZED_SHIFT_CORRECTION_LEDGER_REVERSAL';
        END IF;
    END IF;

    IF NEW.transaction_type = 'EARNING'
       AND NEW.client_request_key LIKE 'shift-wage-correction-%' THEN
        SELECT COUNT(*)
          INTO matching_records
          FROM shift_corrections c
          JOIN journal_entries replacement
            ON replacement.id = NEW.journal_entry_id
           AND replacement.tenant_id = c.tenant_id
           AND replacement.source_type = 'SHIFT_WAGE_CORRECTION'
           AND replacement.source_id = c.id
           AND replacement.posting_key = CONCAT('shift-wage-correction-', c.id)
           AND replacement.status = 'POSTED'
          LEFT JOIN journal_entries reversal
            ON reversal.tenant_id = c.tenant_id
           AND reversal.source_type = 'SHIFT_WAGE_CORRECTION_REVERSAL'
           AND reversal.source_id = c.id
           AND reversal.posting_key = CONCAT('shift-wage-correction-reversal-', c.id)
           AND reversal.reversal_of_entry_id = c.old_wage_journal_entry_id
           AND reversal.status = 'POSTED'
          LEFT JOIN driver_ledger_entries reversed_earning
            ON reversed_earning.tenant_id = c.tenant_id
           AND reversed_earning.driver_id = c.old_driver_id
           AND reversed_earning.shift_id = c.shift_id
           AND reversed_earning.transaction_type = 'CORRECTION_REVERSAL'
           AND reversed_earning.journal_entry_id = reversal.id
           AND reversed_earning.amount = c.old_driver_wage
           AND reversed_earning.balance_effect = -c.old_driver_wage
         WHERE c.tenant_id = NEW.tenant_id
           AND c.shift_id = NEW.shift_id
           AND c.status = 'APPLYING'
           AND NEW.client_request_key = CONCAT('shift-wage-correction-', c.id)
           AND NEW.driver_id = c.new_driver_id
           AND NEW.transaction_date = c.new_shift_date
           AND NEW.amount = c.new_driver_wage
           AND NEW.balance_effect = c.new_driver_wage
           AND (
               (c.old_driver_wage = 0 AND c.old_wage_journal_entry_id IS NULL
                AND c.wage_reversal_journal_entry_id IS NULL
                AND c.old_earning_ledger_entry_id IS NULL
                AND c.reversed_earning_ledger_entry_id IS NULL
                AND reversal.id IS NULL AND reversed_earning.id IS NULL)
               OR
               (c.old_driver_wage > 0 AND c.old_wage_journal_entry_id IS NOT NULL
                AND c.wage_reversal_journal_entry_id = reversal.id
                AND c.reversed_earning_ledger_entry_id = reversed_earning.id)
           );

        IF matching_records <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'UNAUTHORIZED_SHIFT_CORRECTION_LEDGER_EARNING';
        END IF;
    END IF;
END;
