-- Shift eligibility is a configurable property of a Workforce role, not a hard-coded role code.
ALTER TABLE workforce_roles
    ADD COLUMN shift_eligible BOOLEAN NOT NULL DEFAULT FALSE AFTER active;

-- Preserve current operational behavior for the three seeded roles.
UPDATE workforce_roles
SET shift_eligible = TRUE
WHERE code IN ('DRIVER', 'EQUIPMENT_OPERATOR', 'WORKER');
