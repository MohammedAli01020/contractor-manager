-- Remove the temporary metadata guard: completed-shift notes-only edits are
-- an existing supported workflow. The correction-aware V29 trigger continues
-- to validate notes whenever audited operational/financial correction occurs,
-- and already prevents tenant reassignment through journal ownership checks.
DROP TRIGGER IF EXISTS trg_shifts_guard_completed_metadata;
