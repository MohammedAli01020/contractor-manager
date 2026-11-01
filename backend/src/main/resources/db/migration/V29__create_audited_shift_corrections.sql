-- Audited correction envelope for completed shifts.
-- No completed-shift field is unlocked unless an APPLYING row matches both
-- the persisted old snapshot and the exact requested new snapshot.
CREATE TABLE shift_corrections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    shift_id BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    expected_shift_version BIGINT NOT NULL,

    old_shift_date DATE NOT NULL,
    new_shift_date DATE NOT NULL,
    old_shift_type VARCHAR(20) NOT NULL,
    new_shift_type VARCHAR(20) NOT NULL,
    old_site_id BIGINT NOT NULL,
    new_site_id BIGINT NOT NULL,
    old_driver_id BIGINT NOT NULL,
    new_driver_id BIGINT NOT NULL,
    old_equipment_id BIGINT NOT NULL,
    new_equipment_id BIGINT NOT NULL,
    old_driver_wage DECIMAL(15,2) NOT NULL,
    new_driver_wage DECIMAL(15,2) NOT NULL,
    old_price DECIMAL(15,2) NOT NULL,
    new_price DECIMAL(15,2) NOT NULL,
    old_equipment_operating_cost DECIMAL(15,2) NOT NULL,
    new_equipment_operating_cost DECIMAL(15,2) NOT NULL,

    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uk_shift_corrections_tenant_key UNIQUE (tenant_id, idempotency_key),
    INDEX idx_shift_corrections_shift (tenant_id, shift_id, status, id),
    INDEX idx_shift_corrections_actor (tenant_id, actor_user_id, created_at)
) ENGINE=InnoDB;

DROP TRIGGER IF EXISTS trg_shifts_protect_completed;

CREATE TRIGGER trg_shifts_protect_completed
BEFORE UPDATE ON shifts
FOR EACH ROW
BEGIN
    DECLARE matching_corrections INT DEFAULT 0;

    IF NEW.driver_wage_journal_entry_id IS NOT NULL THEN
        SELECT COUNT(*)
          INTO matching_corrections
          FROM journal_entries
         WHERE id = NEW.driver_wage_journal_entry_id
           AND tenant_id = NEW.tenant_id
           AND source_type = 'SHIFT_WAGE'
           AND source_id = NEW.id
           AND posting_key = CONCAT('shift-wage-', NEW.id)
           AND status = 'POSTED';

        IF matching_corrections <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_JOURNAL_MISMATCH';
        END IF;
    END IF;

    IF NEW.status = 'COMPLETED'
       AND OLD.status <> 'COMPLETED' THEN
        IF NEW.driver_wage IS NULL OR NEW.driver_wage <= 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_REQUIRED';
        END IF;
        IF NEW.driver_wage_journal_entry_id IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_WAGE_JOURNAL_REQUIRED';
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
           AND c.new_equipment_operating_cost = NEW.equipment_operating_cost;

        IF matching_corrections <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'COMPLETED_SHIFT_IMMUTABLE';
        END IF;
    END IF;
END;

DROP TRIGGER IF EXISTS trg_shifts_mark_correction_applied;

CREATE TRIGGER trg_shifts_mark_correction_applied
AFTER UPDATE ON shifts
FOR EACH ROW
BEGIN
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
        UPDATE shift_corrections
           SET status = 'APPLIED',
               applied_at = UTC_TIMESTAMP(6),
               updated_at = UTC_TIMESTAMP(6),
               version = version + 1
         WHERE tenant_id = OLD.tenant_id
           AND shift_id = OLD.id
           AND status = 'APPLYING';
    END IF;
END;
