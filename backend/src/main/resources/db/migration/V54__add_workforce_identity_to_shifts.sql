-- Bind every historical shift to the canonical Workforce identity of its legacy Driver projection.
-- driver_id remains in place for compatibility with the current shift APIs and financial ledger.
ALTER TABLE drivers
    ADD UNIQUE KEY uq_drivers_id_tenant_workforce_member (id, tenant_id, workforce_member_id);

ALTER TABLE shifts
    ADD COLUMN workforce_member_id BIGINT NULL AFTER driver_id;

UPDATE shifts s
JOIN drivers d
  ON d.id = s.driver_id
 AND d.tenant_id = s.tenant_id
SET s.workforce_member_id = d.workforce_member_id;

-- Fail the migration rather than silently leaving any historical shift without an identity.
ALTER TABLE shifts
    MODIFY COLUMN workforce_member_id BIGINT NOT NULL,
    ADD KEY ix_shifts_workforce_member_tenant (workforce_member_id, tenant_id),
    ADD CONSTRAINT fk_shifts_workforce_member
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id),
    ADD CONSTRAINT fk_shifts_driver_workforce_identity
        FOREIGN KEY (driver_id, tenant_id, workforce_member_id)
        REFERENCES drivers (id, tenant_id, workforce_member_id);
