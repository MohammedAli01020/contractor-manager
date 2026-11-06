-- WF-08A: add immutable before/after compensation snapshots to audited corrections.
-- Nullable pairs preserve compatibility with correction rows created before this migration.
ALTER TABLE shift_corrections
    ADD COLUMN old_compensation_basis VARCHAR(20) NULL,
    ADD COLUMN new_compensation_basis VARCHAR(20) NULL,
    ADD COLUMN old_compensation_rate DECIMAL(15,2) NULL,
    ADD COLUMN new_compensation_rate DECIMAL(15,2) NULL,
    ADD COLUMN old_compensation_unit_code VARCHAR(30) NULL,
    ADD COLUMN new_compensation_unit_code VARCHAR(30) NULL,
    ADD COLUMN old_compensation_currency_code VARCHAR(3) NULL,
    ADD COLUMN new_compensation_currency_code VARCHAR(3) NULL,
    ADD COLUMN old_compensation_calculated_amount DECIMAL(15,2) NULL,
    ADD COLUMN new_compensation_calculated_amount DECIMAL(15,2) NULL,
    ADD COLUMN old_actual_hours DECIMAL(8,2) NULL,
    ADD COLUMN new_actual_hours DECIMAL(8,2) NULL,
    ADD COLUMN old_actual_quantity DECIMAL(12,3) NULL,
    ADD COLUMN new_actual_quantity DECIMAL(12,3) NULL;
