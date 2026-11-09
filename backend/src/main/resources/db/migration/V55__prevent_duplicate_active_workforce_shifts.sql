-- Workforce is the canonical person identity. Keep the legacy driver guard during
-- the compatibility period, and add a tenant-scoped database invariant on Workforce.
-- MySQL UNIQUE indexes allow multiple NULL values, so completed/cancelled shifts
-- remain unrestricted.
--
-- Preflight before deployment; resolve any returned rows explicitly:
-- SELECT tenant_id, workforce_member_id, COUNT(*)
-- FROM shifts
-- WHERE status = 'IN_PROGRESS'
-- GROUP BY tenant_id, workforce_member_id
-- HAVING COUNT(*) > 1;

ALTER TABLE shifts
    ADD COLUMN active_workforce_member_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN status = 'IN_PROGRESS' THEN workforce_member_id ELSE NULL END
        ) STORED,
    ADD CONSTRAINT uq_shifts_one_active_workforce_member
        UNIQUE (tenant_id, active_workforce_member_id);
