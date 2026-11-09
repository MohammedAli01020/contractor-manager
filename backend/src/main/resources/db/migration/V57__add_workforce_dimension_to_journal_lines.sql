-- Add the canonical Workforce dimension for new journal lines.
-- Existing posted journal lines are immutable (V23 database triggers); do not UPDATE
-- historical rows to backfill this column. Their driver_id remains the historical
-- dimension and their workforce_member_id intentionally stays NULL.
ALTER TABLE journal_lines
    ADD COLUMN workforce_member_id BIGINT NULL AFTER driver_id,
    ADD KEY ix_journal_lines_workforce (tenant_id, workforce_member_id, journal_entry_id),
    ADD CONSTRAINT fk_journal_lines_workforce_member
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id);
