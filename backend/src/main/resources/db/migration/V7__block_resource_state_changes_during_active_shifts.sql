-- Protect active-shift resource invariants even when rows are updated outside the REST services.
-- Keep these triggers narrow: unrelated edits (name, phone, rates, prices, notes) remain allowed.
CREATE TRIGGER trg_drivers_block_unavailable_during_shift
BEFORE UPDATE ON drivers
FOR EACH ROW
BEGIN
    IF ((NEW.availability <> 'AVAILABLE' OR NEW.active = FALSE)
        AND (OLD.availability = 'AVAILABLE' OR OLD.active = TRUE)
        AND EXISTS (
            SELECT 1
            FROM shifts s
            WHERE s.tenant_id = OLD.tenant_id
              AND s.driver_id = OLD.id
              AND s.status = 'IN_PROGRESS'
        )) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ACTIVE_SHIFT_DRIVER_RUNNING';
    END IF;
END;

CREATE TRIGGER trg_equipment_block_unavailable_during_shift
BEFORE UPDATE ON equipment
FOR EACH ROW
BEGIN
    IF ((NEW.status <> 'AVAILABLE' OR NEW.active = FALSE)
        AND (OLD.status = 'AVAILABLE' OR OLD.active = TRUE)
        AND EXISTS (
            SELECT 1
            FROM shifts s
            WHERE s.tenant_id = OLD.tenant_id
              AND s.equipment_id = OLD.id
              AND s.status = 'IN_PROGRESS'
        )) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ACTIVE_SHIFT_EQUIPMENT_RUNNING';
    END IF;
END;
