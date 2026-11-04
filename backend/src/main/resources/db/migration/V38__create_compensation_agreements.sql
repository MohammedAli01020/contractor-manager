-- Effective-dated contractual compensation terms. No earnings or accounting entries are created here.
-- Keep the assignment/member tuple enforceable for agreement rows.
ALTER TABLE workforce_assignments
    ADD UNIQUE KEY uq_workforce_assignments_id_tenant_member
        (id, tenant_id, workforce_member_id);

CREATE TABLE compensation_agreements (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT        NOT NULL,
    workforce_member_id     BIGINT        NOT NULL,
    workforce_assignment_id BIGINT        NOT NULL,
    agreement_reference     VARCHAR(80)   NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    effective_from          DATE          NOT NULL,
    effective_to            DATE          NULL,
    currency_code           CHAR(3)       NOT NULL,
    source                  VARCHAR(40)   NOT NULL DEFAULT 'MANUAL',
    approved_by_user_id     BIGINT        NULL,
    approved_at             DATETIME(6)   NULL,
    created_by_user_id      BIGINT        NOT NULL,
    updated_by_user_id      BIGINT        NOT NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_compensation_agreements_id_tenant (id, tenant_id),
    UNIQUE KEY uq_compensation_agreements_tenant_reference (tenant_id, agreement_reference),
    KEY ix_compensation_agreements_member_dates
        (tenant_id, workforce_member_id, effective_from, effective_to, status),
    KEY ix_compensation_agreements_assignment_dates
        (tenant_id, workforce_assignment_id, effective_from, effective_to, status),
    CONSTRAINT fk_compensation_agreements_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_compensation_agreements_member
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id),
    CONSTRAINT fk_compensation_agreements_assignment_member
        FOREIGN KEY (workforce_assignment_id, tenant_id, workforce_member_id)
        REFERENCES workforce_assignments (id, tenant_id, workforce_member_id),
    CONSTRAINT fk_compensation_agreements_approved_by
        FOREIGN KEY (approved_by_user_id, tenant_id)
        REFERENCES users (id, tenant_id),
    CONSTRAINT fk_compensation_agreements_created_by
        FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES users (id, tenant_id),
    CONSTRAINT fk_compensation_agreements_updated_by
        FOREIGN KEY (updated_by_user_id, tenant_id)
        REFERENCES users (id, tenant_id),
    CONSTRAINT ck_compensation_agreements_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_compensation_agreements_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'SUPERSEDED', 'EXPIRED')),
    CONSTRAINT ck_compensation_agreements_source
        CHECK (source IN ('MANUAL', 'LEGACY_DRIVER_RATE_MIGRATION', 'IMPORT')),
    CONSTRAINT ck_compensation_agreements_approval
        CHECK ((status = 'DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL)
            OR (status <> 'DRAFT' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE compensation_components (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id                BIGINT        NOT NULL,
    compensation_agreement_id BIGINT       NOT NULL,
    component_code           VARCHAR(60)   NOT NULL,
    display_name             VARCHAR(120)  NOT NULL,
    basis                    VARCHAR(20)   NOT NULL,
    rate                     DECIMAL(15,2) NOT NULL,
    unit_code                VARCHAR(30)   NULL,
    shift_type               VARCHAR(20)   NULL,
    sort_order               INT           NOT NULL DEFAULT 0,
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_compensation_components_id_tenant (id, tenant_id),
    UNIQUE KEY uq_compensation_components_agreement_code
        (compensation_agreement_id, tenant_id, component_code),
    UNIQUE KEY uq_compensation_components_agreement_shift
        (compensation_agreement_id, tenant_id, shift_type),
    KEY ix_compensation_components_tenant_basis
        (tenant_id, basis, unit_code, shift_type),
    CONSTRAINT fk_compensation_components_agreement
        FOREIGN KEY (compensation_agreement_id, tenant_id)
        REFERENCES compensation_agreements (id, tenant_id),
    CONSTRAINT ck_compensation_components_basis
        CHECK (basis IN ('SHIFT', 'HOUR', 'DAY', 'QUANTITY', 'FIXED')),
    CONSTRAINT ck_compensation_components_rate
        CHECK (rate >= 0),
    CONSTRAINT ck_compensation_components_shift
        CHECK ((basis = 'SHIFT' AND shift_type IS NOT NULL AND shift_type IN ('MORNING', 'EVENING', 'NIGHT'))
            OR (basis <> 'SHIFT' AND shift_type IS NULL)),
    CONSTRAINT ck_compensation_components_unit
        CHECK ((basis IN ('HOUR', 'DAY', 'QUANTITY') AND unit_code IS NOT NULL AND TRIM(unit_code) <> '')
            OR (basis = 'FIXED' AND unit_code IS NOT NULL AND unit_code IN ('MONTH', 'WEEK', 'DAY'))
            OR (basis = 'SHIFT' AND (unit_code IS NULL OR TRIM(unit_code) <> '')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
