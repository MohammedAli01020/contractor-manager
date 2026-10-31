-- Strengthen the worker ledger and posted-shift invariants at the database boundary.
-- MySQL 8.0 supports stored generated columns, CHECK constraints and SIGNAL triggers.

-- A shift can produce exactly one EARNING ledger row, regardless of retries or code paths.
ALTER TABLE shifts
    ADD UNIQUE KEY uq_shifts_id_tenant_driver (id, tenant_id, driver_id);

ALTER TABLE driver_ledger_entries
    ADD COLUMN earning_shift_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN transaction_type = 'EARNING' THEN shift_id ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_driver_ledger_one_shift_earning (tenant_id, earning_shift_id),
    ADD CONSTRAINT fk_driver_ledger_shift_driver
        FOREIGN KEY (shift_id, tenant_id, driver_id)
        REFERENCES shifts (id, tenant_id, driver_id),
    ADD CONSTRAINT chk_driver_ledger_earning_shift
        CHECK (transaction_type <> 'EARNING' OR shift_id IS NOT NULL);

-- Once a wage journal is attached, financial/source fields and completion state are immutable.
-- Notes remain editable as operational commentary; financial corrections must use reversal/adjustment entries.
CREATE TRIGGER trg_shifts_protect_posted_wage
BEFORE UPDATE ON shifts
FOR EACH ROW
BEGIN
    IF OLD.driver_wage_journal_entry_id IS NOT NULL
       AND (
           NOT (NEW.driver_wage_journal_entry_id <=> OLD.driver_wage_journal_entry_id)
           OR NEW.status <> 'COMPLETED'
           OR NOT (NEW.shift_date <=> OLD.shift_date)
           OR NOT (NEW.shift_type <=> OLD.shift_type)
           OR NOT (NEW.site_id <=> OLD.site_id)
           OR NOT (NEW.driver_id <=> OLD.driver_id)
           OR NOT (NEW.equipment_id <=> OLD.equipment_id)
           OR NOT (NEW.driver_wage <=> OLD.driver_wage)
           OR NOT (NEW.price <=> OLD.price)
           OR NOT (NEW.equipment_operating_cost <=> OLD.equipment_operating_cost)
           OR NOT (NEW.started_at <=> OLD.started_at)
           OR (
               NOT (NEW.finished_at <=> OLD.finished_at)
               AND NOT (
                   OLD.status = 'IN_PROGRESS'
                   AND NEW.status = 'COMPLETED'
                   AND OLD.finished_at IS NULL
                   AND NEW.finished_at IS NOT NULL
               )
           )
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'POSTED_SHIFT_WAGE_IMMUTABLE';
    END IF;
END;

CREATE TRIGGER trg_shifts_prevent_delete_posted_wage
BEFORE DELETE ON shifts
FOR EACH ROW
BEGIN
    IF OLD.driver_wage_journal_entry_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'POSTED_SHIFT_WAGE_CANNOT_BE_DELETED';
    END IF;
END;

-- The worker ledger is append-only. Correct mistakes with compensating entries, never UPDATE/DELETE.
CREATE TRIGGER trg_driver_ledger_prevent_update
BEFORE UPDATE ON driver_ledger_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'DRIVER_LEDGER_IS_APPEND_ONLY';
END;

CREATE TRIGGER trg_driver_ledger_prevent_delete
BEFORE DELETE ON driver_ledger_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'DRIVER_LEDGER_IS_APPEND_ONLY';
END;
