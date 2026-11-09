-- Tenant-owned catalog for configurable Workforce earnings and deduction definitions.
-- This migration intentionally creates definitions only: no ledger writes or journal behavior.
CREATE TABLE workforce_financial_item_definitions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    item_code VARCHAR(60) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    item_family VARCHAR(20) NOT NULL,
    calculation_method VARCHAR(24) NOT NULL,
    allow_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    allow_one_off BOOLEAN NOT NULL DEFAULT TRUE,
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_workforce_financial_items_id_tenant (id, tenant_id),
    UNIQUE KEY uq_workforce_financial_items_tenant_code (tenant_id, item_code),
    KEY ix_workforce_financial_items_tenant_active_name (tenant_id, active, display_name),
    CONSTRAINT fk_workforce_financial_items_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_workforce_financial_items_family
        CHECK (item_family IN ('EARNING', 'DEDUCTION')),
    CONSTRAINT ck_workforce_financial_items_calculation
        CHECK (calculation_method IN ('FIXED', 'PER_HOUR', 'PER_DAY', 'PER_SHIFT', 'PER_QUANTITY', 'MANUAL')),
    CONSTRAINT ck_workforce_financial_items_usage
        CHECK (allow_recurring = TRUE OR allow_one_off = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
