-- Actual hours are recorded explicitly at finish for hourly-compensated shifts.
-- The hourly rate is frozen at start; no amount is recognized until measured hours are supplied.
ALTER TABLE shifts
    ADD COLUMN actual_hours DECIMAL(8,2) NULL,
    ADD CONSTRAINT ck_shifts_actual_hours_basis
        CHECK (
            (compensation_basis = 'HOUR' AND (actual_hours IS NULL OR actual_hours > 0))
            OR (compensation_basis <> 'HOUR' AND actual_hours IS NULL)
        ),
    ADD CONSTRAINT ck_shifts_hourly_completion
        CHECK (
            compensation_basis <> 'HOUR'
            OR status <> 'COMPLETED'
            OR (
                actual_hours IS NOT NULL
                AND driver_wage = ROUND(compensation_rate * actual_hours, 2)
                AND compensation_calculated_amount = driver_wage
            )
        );
