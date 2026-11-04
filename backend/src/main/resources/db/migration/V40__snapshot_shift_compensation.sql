-- Freeze the compensation decision used by each shift.
-- Existing shifts keep their already-recorded wage; never recalculate historical wages from current driver rates.
ALTER TABLE shifts
    ADD COLUMN wage_source VARCHAR(30) NULL,
    ADD COLUMN compensation_agreement_id BIGINT NULL,
    ADD COLUMN compensation_component_id BIGINT NULL,
    ADD COLUMN compensation_agreement_reference VARCHAR(80) NULL,
    ADD COLUMN compensation_component_code VARCHAR(60) NULL,
    ADD COLUMN compensation_component_name VARCHAR(120) NULL,
    ADD COLUMN compensation_basis VARCHAR(20) NULL,
    ADD COLUMN compensation_currency_code VARCHAR(3) NULL,
    ADD COLUMN compensation_unit_code VARCHAR(30) NULL,
    ADD COLUMN compensation_rate DECIMAL(15,2) NULL,
    ADD COLUMN compensation_calculated_amount DECIMAL(15,2) NULL;

UPDATE shifts
SET wage_source = 'LEGACY_DRIVER_RATE',
    compensation_component_code = 'LEGACY_DRIVER_RATE',
    compensation_component_name = 'Legacy driver rate',
    compensation_basis = 'SHIFT',
    compensation_rate = driver_wage,
    compensation_calculated_amount = driver_wage
WHERE wage_source IS NULL;

ALTER TABLE shifts
    MODIFY COLUMN wage_source VARCHAR(30) NOT NULL,
    MODIFY COLUMN compensation_component_code VARCHAR(60) NOT NULL,
    MODIFY COLUMN compensation_component_name VARCHAR(120) NOT NULL,
    MODIFY COLUMN compensation_basis VARCHAR(20) NOT NULL,
    MODIFY COLUMN compensation_rate DECIMAL(15,2) NOT NULL,
    MODIFY COLUMN compensation_calculated_amount DECIMAL(15,2) NOT NULL,
    ADD KEY ix_shifts_compensation_agreement (tenant_id, compensation_agreement_id),
    ADD KEY ix_shifts_compensation_component (tenant_id, compensation_component_id),
    ADD CONSTRAINT ck_shifts_wage_source CHECK (wage_source IN ('AGREEMENT', 'LEGACY_DRIVER_RATE')),
    ADD CONSTRAINT ck_shifts_compensation_basis CHECK (compensation_basis IN ('SHIFT', 'HOUR', 'DAY', 'QUANTITY', 'FIXED')),
    ADD CONSTRAINT ck_shifts_compensation_amount CHECK (compensation_rate >= 0 AND compensation_calculated_amount >= 0),
    ADD CONSTRAINT fk_shifts_compensation_agreement
        FOREIGN KEY (compensation_agreement_id, tenant_id)
        REFERENCES compensation_agreements (id, tenant_id),
    ADD CONSTRAINT fk_shifts_compensation_component
        FOREIGN KEY (compensation_component_id, tenant_id)
        REFERENCES compensation_components (id, tenant_id);
