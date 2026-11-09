-- Add the canonical Workforce dimension without rewriting immutable journal history.
-- Existing driver_id values remain untouched as historical provenance.
ALTER TABLE journal_lines
    ADD COLUMN workforce_member_id BIGINT NULL AFTER driver_id,
    ADD KEY ix_journal_lines_workforce (tenant_id, workforce_member_id, journal_entry_id),
    ADD CONSTRAINT fk_journal_lines_workforce_member
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id);

-- Backfill only through the tenant-scoped canonical mapping.
UPDATE journal_lines jl
JOIN drivers d
  ON d.id = jl.driver_id
 AND d.tenant_id = jl.tenant_id
SET jl.workforce_member_id = d.workforce_member_id
WHERE jl.driver_id IS NOT NULL;
