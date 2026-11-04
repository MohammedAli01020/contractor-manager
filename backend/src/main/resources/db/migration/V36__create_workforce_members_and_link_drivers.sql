-- Introduce one tenant-scoped workforce identity for each existing driver/worker.
-- A temporary migration key maps rows deterministically; no IDs are inferred from names or auto-increment order.

CREATE TABLE workforce_members (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    full_name   VARCHAR(150)  NOT NULL,
    phone       VARCHAR(30)   NULL,
    national_id VARCHAR(30)   NULL,
    notes       VARCHAR(500)  NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    -- Temporary deterministic source key; removed after the backfill.
    migration_driver_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workforce_members_id_tenant (id, tenant_id),
    UNIQUE KEY uq_workforce_members_tenant_driver_migration (tenant_id, migration_driver_id),
    KEY ix_workforce_members_tenant_name (tenant_id, full_name, id),
    CONSTRAINT fk_workforce_members_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE drivers
    ADD COLUMN workforce_member_id BIGINT NULL AFTER tenant_id;

-- Preserve every legacy driver ID and copy its identity fields and audit timestamps.
INSERT INTO workforce_members (
    tenant_id, full_name, phone, national_id, notes, active, version,
    created_at, updated_at, migration_driver_id
)
SELECT
    d.tenant_id, d.name, d.phone, d.national_id, d.notes, d.active, d.version,
    d.created_at, d.updated_at, d.id
FROM drivers d
ORDER BY d.id;

UPDATE drivers d
JOIN workforce_members wm
  ON wm.tenant_id = d.tenant_id
 AND wm.migration_driver_id = d.id
SET d.workforce_member_id = wm.id;

-- Drop the temporary bridge only after every driver has been linked.
ALTER TABLE workforce_members
    DROP INDEX uq_workforce_members_tenant_driver_migration,
    DROP COLUMN migration_driver_id;

-- NOT NULL is the final database-level proof that no legacy driver was skipped.
ALTER TABLE drivers
    MODIFY COLUMN workforce_member_id BIGINT NOT NULL,
    ADD UNIQUE KEY uq_drivers_tenant_workforce_member (tenant_id, workforce_member_id),
    ADD KEY ix_drivers_workforce_member_tenant (workforce_member_id, tenant_id),
    ADD CONSTRAINT fk_drivers_workforce_member_tenant
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id);
