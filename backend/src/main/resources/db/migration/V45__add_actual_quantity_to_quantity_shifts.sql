-- Actual quantity is explicitly measured when a quantity-compensated shift is finished.
-- The contractual unit/rate are frozen at start; no wage is recognized before measurement.
ALTER TABLE shifts
    ADD COLUMN actual_quantity DECIMAL(12,3) NULL,
    ADD CONSTRAINT ck_shifts_actual_quantity_basis
        CHECK (
            (compensation_basis = 'QUANTITY' AND (actual_quantity IS NULL OR actual_quantity > 0))
            OR (compensation_basis <> 'QUANTITY' AND actual_quantity IS NULL)
        ),
    ADD CONSTRAINT ck_shifts_quantity_completion
        CHECK (
            compensation_basis <> 'QUANTITY'
            OR status <> 'COMPLETED'
            OR (
                actual_quantity IS NOT NULL
                AND driver_wage = ROUND(compensation_rate * actual_quantity, 2)
                AND compensation_calculated_amount = driver_wage
            )
        );
