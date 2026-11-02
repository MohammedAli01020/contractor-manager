-- Applied shift-correction records are part of the financial audit trail.
-- They may only transition from APPLYING to APPLIED after the shift and all
-- reversal/replacement journal and driver-ledger references agree. Once applied,
-- the record cannot be edited or deleted.
DROP TRIGGER IF EXISTS trg_shift_corrections_guard_update;

CREATE TRIGGER trg_shift_corrections_guard_update
BEFORE UPDATE ON shift_corrections
FOR EACH ROW
BEGIN
    DECLARE matching_records INT DEFAULT 0;

    IF OLD.status = 'APPLIED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SHIFT_CORRECTION_AUDIT_IMMUTABLE';
    END IF;

    IF OLD.status <> 'APPLYING'
       OR NEW.status NOT IN ('APPLYING', 'APPLIED')
       OR (NEW.tenant_id <> OLD.tenant_id)
       OR (NEW.shift_id <> OLD.shift_id)
       OR (NEW.idempotency_key <> OLD.idempotency_key)
       OR (NEW.reason <> OLD.reason)
       OR (NEW.actor_user_id <> OLD.actor_user_id)
       OR (NEW.expected_shift_version <> OLD.expected_shift_version)
       OR (NEW.old_shift_date <> OLD.old_shift_date)
       OR (NEW.new_shift_date <> OLD.new_shift_date)
       OR (NEW.old_shift_type <> OLD.old_shift_type)
       OR (NEW.new_shift_type <> OLD.new_shift_type)
       OR (NEW.old_site_id <> OLD.old_site_id)
       OR (NEW.new_site_id <> OLD.new_site_id)
       OR (NEW.old_driver_id <> OLD.old_driver_id)
       OR (NEW.new_driver_id <> OLD.new_driver_id)
       OR (NEW.old_equipment_id <> OLD.old_equipment_id)
       OR (NEW.new_equipment_id <> OLD.new_equipment_id)
       OR (NEW.old_driver_wage <> OLD.old_driver_wage)
       OR (NEW.new_driver_wage <> OLD.new_driver_wage)
       OR (NEW.old_price <> OLD.old_price)
       OR (NEW.new_price <> OLD.new_price)
       OR (NEW.old_equipment_operating_cost <> OLD.old_equipment_operating_cost)
       OR (NEW.new_equipment_operating_cost <> OLD.new_equipment_operating_cost)
       OR NOT (NEW.old_notes <=> OLD.old_notes)
       OR NOT (NEW.new_notes <=> OLD.new_notes)
       OR (NEW.old_wage_journal_entry_id <> OLD.old_wage_journal_entry_id)
       OR (NEW.old_earning_ledger_entry_id <> OLD.old_earning_ledger_entry_id)
       OR (OLD.wage_reversal_journal_entry_id IS NOT NULL
           AND NOT (NEW.wage_reversal_journal_entry_id <=> OLD.wage_reversal_journal_entry_id))
       OR (OLD.replacement_wage_journal_entry_id IS NOT NULL
           AND NOT (NEW.replacement_wage_journal_entry_id <=> OLD.replacement_wage_journal_entry_id))
       OR (OLD.reversed_earning_ledger_entry_id IS NOT NULL
           AND NOT (NEW.reversed_earning_ledger_entry_id <=> OLD.reversed_earning_ledger_entry_id))
       OR (OLD.replacement_earning_ledger_entry_id IS NOT NULL
           AND NOT (NEW.replacement_earning_ledger_entry_id <=> OLD.replacement_earning_ledger_entry_id))
       OR (OLD.applied_at IS NOT NULL AND NOT (NEW.applied_at <=> OLD.applied_at)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'SHIFT_CORRECTION_AUDIT_IMMUTABLE';
    END IF;

    IF NEW.status = 'APPLYING' THEN
        IF NEW.applied_at IS NOT NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'SHIFT_CORRECTION_AUDIT_IMMUTABLE';
        END IF;
    ELSE
        SELECT COUNT(*)
          INTO matching_records
          FROM shifts s
         WHERE s.tenant_id = NEW.tenant_id
           AND s.id = NEW.shift_id
           AND s.status = 'COMPLETED'
           AND s.shift_date = NEW.new_shift_date
           AND s.shift_type = NEW.new_shift_type
           AND s.site_id = NEW.new_site_id
           AND s.driver_id = NEW.new_driver_id
           AND NEW.new_driver_id = NEW.old_driver_id
           AND s.equipment_id = NEW.new_equipment_id
           AND s.driver_wage = NEW.new_driver_wage
           AND s.price = NEW.new_price
           AND s.equipment_operating_cost = NEW.new_equipment_operating_cost
           AND (s.notes <=> NEW.new_notes)
           AND s.driver_wage_journal_entry_id = NEW.replacement_wage_journal_entry_id
           AND NEW.applied_at IS NOT NULL
           AND EXISTS (
               SELECT 1 FROM journal_entries r
                WHERE r.id = NEW.wage_reversal_journal_entry_id
                  AND r.tenant_id = NEW.tenant_id
                  AND r.source_type = 'SHIFT_WAGE_CORRECTION_REVERSAL'
                  AND r.source_id = NEW.id
                  AND r.posting_key = CONCAT('shift-wage-correction-reversal-', NEW.id)
                  AND r.reversal_of_entry_id = NEW.old_wage_journal_entry_id
                  AND r.status = 'POSTED'
           )
           AND EXISTS (
               SELECT 1 FROM journal_entries j
                WHERE j.id = NEW.replacement_wage_journal_entry_id
                  AND j.tenant_id = NEW.tenant_id
                  AND j.source_type = 'SHIFT_WAGE_CORRECTION'
                  AND j.source_id = NEW.id
                  AND j.posting_key = CONCAT('shift-wage-correction-', NEW.id)
                  AND j.status = 'POSTED'
           )
           AND EXISTS (
               SELECT 1 FROM driver_ledger_entries e
                WHERE e.id = NEW.old_earning_ledger_entry_id
                  AND e.tenant_id = NEW.tenant_id
                  AND e.driver_id = NEW.old_driver_id
                  AND e.shift_id = NEW.shift_id
                  AND e.transaction_type = 'EARNING'
                  AND e.journal_entry_id = NEW.old_wage_journal_entry_id
                  AND e.amount = NEW.old_driver_wage
                  AND e.balance_effect = NEW.old_driver_wage
           )
           AND EXISTS (
               SELECT 1 FROM driver_ledger_entries e
                WHERE e.id = NEW.reversed_earning_ledger_entry_id
                  AND e.tenant_id = NEW.tenant_id
                  AND e.driver_id = NEW.old_driver_id
                  AND e.shift_id = NEW.shift_id
                  AND e.transaction_type = 'CORRECTION_REVERSAL'
                  AND e.journal_entry_id = NEW.wage_reversal_journal_entry_id
                  AND e.amount = NEW.old_driver_wage
                  AND e.balance_effect = -NEW.old_driver_wage
           )
           AND EXISTS (
               SELECT 1 FROM driver_ledger_entries e
                WHERE e.id = NEW.replacement_earning_ledger_entry_id
                  AND e.tenant_id = NEW.tenant_id
                  AND e.driver_id = NEW.new_driver_id
                  AND e.shift_id = NEW.shift_id
                  AND e.transaction_type = 'EARNING'
                  AND e.journal_entry_id = NEW.replacement_wage_journal_entry_id
                  AND e.amount = NEW.new_driver_wage
                  AND e.balance_effect = NEW.new_driver_wage
           );

        IF matching_records <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'SHIFT_CORRECTION_AUDIT_IMMUTABLE';
        END IF;
    END IF;
END;

DROP TRIGGER IF EXISTS trg_shift_corrections_guard_delete;

CREATE TRIGGER trg_shift_corrections_guard_delete
BEFORE DELETE ON shift_corrections
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'SHIFT_CORRECTION_AUDIT_IMMUTABLE';
END;
