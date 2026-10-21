-- Rebuild resource-state guards to use V6's indexed generated columns.
-- A new migration is used instead of editing V7, preserving Flyway checksums.
DROP TRIGGER IF EXISTS trg_drivers_block_unavailable_during_shift;
DROP TRIGGER IF EXISTS trg_equipment_block_unavailable_during_shift;

CREATE TRIGGER trg_drivers_block_unavailable_during_shift
BEFORE UPDATE ON drivers
FOR EACH ROW
BEGIN
    IF (
        (
            (OLD.availability = 'AVAILABLE' AND NEW.availability <> 'AVAILABLE')
            OR (OLD.active = TRUE AND NEW.active = FALSE)
        )
        AND EXISTS (
            SELECT 1
            FROM shifts s
            WHERE s.tenant_id = OLD.tenant_id
              AND s.active_driver_id = OLD.id
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ACTIVE_SHIFT_DRIVER_RUNNING';
    END IF;
END;

CREATE TRIGGER trg_equipment_block_unavailable_during_shift
BEFORE UPDATE ON equipment
FOR EACH ROW
BEGIN
    IF (
        (
            (OLD.status = 'AVAILABLE' AND NEW.status <> 'AVAILABLE')
            OR (OLD.active = TRUE AND NEW.active = FALSE)
        )
        AND EXISTS (
            SELECT 1
            FROM shifts s
            WHERE s.tenant_id = OLD.tenant_id
              AND s.active_equipment_id = OLD.id
        )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ACTIVE_SHIFT_EQUIPMENT_RUNNING';
    END IF;
END;
