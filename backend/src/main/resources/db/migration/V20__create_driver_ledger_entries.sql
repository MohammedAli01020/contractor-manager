CREATE TABLE driver_ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    transaction_date DATE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_effect DECIMAL(15,2) NOT NULL,
    description VARCHAR(300) NOT NULL,
    client_request_key VARCHAR(120) NOT NULL,
    shift_id BIGINT NULL,
    journal_entry_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_driver_ledger_request (tenant_id, client_request_key),
    UNIQUE KEY uq_driver_ledger_journal (tenant_id, journal_entry_id),
    KEY ix_driver_ledger_statement (tenant_id, driver_id, transaction_date, id),
    CONSTRAINT fk_driver_ledger_driver FOREIGN KEY (driver_id, tenant_id)
        REFERENCES drivers (id, tenant_id),
    CONSTRAINT fk_driver_ledger_shift FOREIGN KEY (shift_id, tenant_id)
        REFERENCES shifts (id, tenant_id),
    CONSTRAINT fk_driver_ledger_journal FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES journal_entries (id, tenant_id),
    CONSTRAINT chk_driver_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_driver_ledger_effect CHECK (balance_effect <> 0),
    CONSTRAINT chk_driver_ledger_type CHECK (transaction_type IN ('EARNING','PAYMENT','ADVANCE','ADJUSTMENT'))
) ENGINE=InnoDB;