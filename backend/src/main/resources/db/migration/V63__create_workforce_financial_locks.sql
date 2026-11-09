-- Create a dedicated serialization mutex for each canonical Workforce member.
-- Keep this separate from Driver/Workforce row locks to avoid lock-order inversion
-- with existing identity synchronization paths during the compatibility period.
CREATE TABLE workforce_financial_locks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    workforce_member_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_workforce_financial_lock_member (tenant_id, workforce_member_id),
    CONSTRAINT fk_workforce_financial_lock_member
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id)
) ENGINE=InnoDB;

-- Existing members receive exactly one mutex row before application code begins
-- creating mutexes for newly onboarded members.
INSERT INTO workforce_financial_locks (
    tenant_id, workforce_member_id, version, created_at, updated_at
)
SELECT tenant_id, id, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM workforce_members
ORDER BY tenant_id, id;
