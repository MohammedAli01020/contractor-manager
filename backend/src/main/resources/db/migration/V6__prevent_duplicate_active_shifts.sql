-- Prevent more than one active shift per driver/equipment within a tenant.
-- MySQL UNIQUE indexes permit multiple NULL values, so completed/cancelled shifts remain unrestricted.
-- Preflight before deploying:
-- SELECT tenant_id, driver_id, COUNT(*) FROM shifts WHERE status = 'IN_PROGRESS'
--   GROUP BY tenant_id, driver_id HAVING COUNT(*) > 1;
-- SELECT tenant_id, equipment_id, COUNT(*) FROM shifts WHERE status = 'IN_PROGRESS'
--   GROUP BY tenant_id, equipment_id HAVING COUNT(*) > 1;
-- Resolve any returned rows manually before applying this migration; do not delete business data automatically.

ALTER TABLE shifts
    ADD COLUMN active_driver_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status = 'IN_PROGRESS' THEN driver_id ELSE NULL END) STORED,
    ADD COLUMN active_equipment_id BIGINT
        GENERATED ALWAYS AS (CASE WHEN status = 'IN_PROGRESS' THEN equipment_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_shifts_one_active_driver UNIQUE (tenant_id, active_driver_id),
    ADD CONSTRAINT uq_shifts_one_active_equipment UNIQUE (tenant_id, active_equipment_id);
