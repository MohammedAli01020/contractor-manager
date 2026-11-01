-- V29 replaces the original V22 completed-shift guard with a stricter audited
-- correction-aware guard. Keeping both BEFORE UPDATE triggers causes the legacy
-- V22 trigger to reject every valid correction before the V29 authorization can
-- take effect (POSTED_SHIFT_WAGE_IMMUTABLE).
DROP TRIGGER IF EXISTS trg_shifts_protect_posted_wage;
