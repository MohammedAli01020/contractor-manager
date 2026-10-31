-- Link a completed shift to its one-time wage recognition journal.
ALTER TABLE shifts
    ADD COLUMN driver_wage_journal_entry_id BIGINT NULL,
    ADD UNIQUE KEY uq_shifts_driver_wage_journal (tenant_id, driver_wage_journal_entry_id),
    ADD CONSTRAINT fk_shifts_driver_wage_journal
        FOREIGN KEY (driver_wage_journal_entry_id, tenant_id)
        REFERENCES journal_entries (id, tenant_id);
