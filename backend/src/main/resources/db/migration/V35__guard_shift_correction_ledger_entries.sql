-- Do not let a caller bypass the one-EARNING-per-shift guard by choosing
-- the reserved correction key prefix. Correction ledger movements must be
-- backed by an APPLYING correction record and the exact posted journal.
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
          JOIN journal_entries reversal
            ON reversal.tenant_id = c.tenant_id
           AND reversal.source_type = 'SHIFT_WAGE_CORRECTION_REVERSAL'
           AND reversal.source_id = c.id
           AND reversal.posting_key = CONCAT('shift-wage-correction-reversal-', c.id)
           AND reversal.reversal_of_entry_id = c.old_wage_journal_entry_id
           AND reversal.status = 'POSTED'
          JOIN driver_ledger_entries reversed_earning
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
           AND NEW.balance_effect = c.new_driver_wage;

        IF matching_records <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'UNAUTHORIZED_SHIFT_CORRECTION_LEDGER_EARNING';
        END IF;
    END IF;
END;
