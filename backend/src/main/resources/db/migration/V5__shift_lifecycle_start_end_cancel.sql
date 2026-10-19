ALTER TABLE shifts
    ADD COLUMN started_at DATETIME(6) NULL AFTER shift_date,
    ADD COLUMN ended_at DATETIME(6) NULL AFTER started_at,
    ADD COLUMN cancelled_at DATETIME(6) NULL AFTER ended_at;

ALTER TABLE shifts
    DROP INDEX uq_shifts_equipment_slot,
    DROP INDEX uq_shifts_driver_slot;

ALTER TABLE shifts
    ADD COLUMN in_progress_driver_flag TINYINT
        AS (CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE NULL END) STORED,
    ADD COLUMN in_progress_equipment_flag TINYINT
        AS (CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_shifts_driver_in_progress (tenant_id, driver_id, in_progress_driver_flag),
    ADD UNIQUE KEY uq_shifts_equipment_in_progress (tenant_id, equipment_id, in_progress_equipment_flag),
    ADD KEY ix_shifts_tenant_status (tenant_id, status);

UPDATE shifts
SET started_at = COALESCE(started_at, created_at)
WHERE started_at IS NULL;

UPDATE shifts
SET ended_at = COALESCE(ended_at, updated_at, created_at)
WHERE status = 'COMPLETED' AND ended_at IS NULL;

UPDATE shifts
SET cancelled_at = COALESCE(cancelled_at, updated_at, created_at)
WHERE status = 'CANCELLED' AND cancelled_at IS NULL;
