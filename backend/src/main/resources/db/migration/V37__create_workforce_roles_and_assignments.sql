-- Tenant-owned role catalogue and effective-dated workforce assignments.
CREATE TABLE workforce_roles (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    system_defined BOOLEAN     NOT NULL DEFAULT FALSE,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_workforce_roles_id_tenant (id, tenant_id),
    UNIQUE KEY uq_workforce_roles_tenant_code (tenant_id, code),
    KEY ix_workforce_roles_tenant_active (tenant_id, active, name),
    CONSTRAINT fk_workforce_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed common role definitions for every existing tenant. Custom roles remain tenant-local.
INSERT INTO workforce_roles (tenant_id, code, name, system_defined, active)
SELECT t.id, r.code, r.name, TRUE, TRUE
FROM tenants t
CROSS JOIN (
    SELECT 'DRIVER' AS code, 'سائق' AS name
    UNION ALL SELECT 'EQUIPMENT_OPERATOR', 'مشغل معدة'
    UNION ALL SELECT 'WORKER', 'عامل'
    UNION ALL SELECT 'TECHNICIAN', 'فني'
    UNION ALL SELECT 'SUPERVISOR', 'مشرف'
    UNION ALL SELECT 'ENGINEER', 'مهندس'
    UNION ALL SELECT 'ADMINISTRATIVE', 'إداري'
) r;

CREATE TABLE workforce_assignments (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT       NOT NULL,
    workforce_member_id BIGINT       NOT NULL,
    workforce_role_id   BIGINT       NOT NULL,
    site_id             BIGINT       NULL,
    effective_from      DATE         NOT NULL,
    effective_to        DATE         NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    notes               VARCHAR(500) NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_workforce_assignments_id_tenant (id, tenant_id),
    KEY ix_workforce_assignments_member_dates (tenant_id, workforce_member_id, effective_from, effective_to),
    KEY ix_workforce_assignments_role_dates (tenant_id, workforce_role_id, effective_from, effective_to),
    KEY ix_workforce_assignments_site_tenant (site_id, tenant_id),
    CONSTRAINT fk_workforce_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_workforce_assignments_member FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id),
    CONSTRAINT fk_workforce_assignments_role FOREIGN KEY (workforce_role_id, tenant_id)
        REFERENCES workforce_roles (id, tenant_id),
    CONSTRAINT fk_workforce_assignments_site FOREIGN KEY (site_id, tenant_id)
        REFERENCES sites (id, tenant_id),
    CONSTRAINT ck_workforce_assignments_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_workforce_assignments_status CHECK (status IN ('ACTIVE', 'ENDED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
