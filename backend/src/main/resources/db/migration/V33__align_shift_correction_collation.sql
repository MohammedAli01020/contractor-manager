-- V29 creates its audit envelope without an explicit table collation.
-- The existing shifts and journal tables use utf8mb4_unicode_ci. Align the
-- correction snapshot table so trigger comparisons between its VARCHAR columns
-- and the legacy financial/shift tables do not fail with MySQL error 1267.
ALTER TABLE shift_corrections
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
