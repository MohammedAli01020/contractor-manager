-- Add the canonical Workforce dimension for new journal lines.
-- Existing posted journal lines are protected by V23 immutability triggers.
-- Do not UPDATE historical rows: their driver_id remains the historical dimension
-- and workforce_member_id intentionally stays NULL for those immutable entries.
ALTER TABLE journal_lines
    ADD COLUMN workforce_member_id BIGINT NULL AFTER driver_id,
    ADD KEY ix_journal_lines_workforce (tenant_id, workforce_member_id, journal_entry_id),
    ADD CONSTRAINT fk_journal_lines_workforce_member
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id);
