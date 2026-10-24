-- Track the latest activation time independently from the original record creation time.
-- Existing rows use created_at as a safe historical fallback; their actual historical restart time
-- cannot be reconstructed reliably from this column alone.
ALTER TABLE shifts
    ADD COLUMN started_at DATETIME(6) NULL;

UPDATE shifts
SET started_at = created_at
WHERE started_at IS NULL;

ALTER TABLE shifts
    MODIFY COLUMN started_at DATETIME(6) NOT NULL;
