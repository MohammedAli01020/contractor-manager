-- Double-entry accounting foundation. Additive migration; never edit after deployment.
CREATE TABLE chart_of_accounts (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT        NOT NULL,
    account_code      VARCHAR(30)   NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    account_type       VARCHAR(20)   NOT NULL,
    parent_account_id BIGINT        NULL,
    system_managed    BOOLEAN       NOT NULL DEFAULT FALSE,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_chart_accounts_id_tenant (id, tenant_id),
    UNIQUE KEY uq_chart_accounts_code_tenant (tenant_id, account_code),
    KEY ix_chart_accounts_tenant_type (tenant_id, account_type, active),
    CONSTRAINT fk_chart_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_chart_accounts_parent FOREIGN KEY (parent_account_id, tenant_id)
        REFERENCES chart_of_accounts (id, tenant_id),
    CONSTRAINT ck_chart_accounts_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ck_chart_accounts_code CHECK (CHAR_LENGTH(TRIM(account_code)) > 0),
    CONSTRAINT ck_chart_accounts_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE journal_entries (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT        NOT NULL,
    posting_key           VARCHAR(120)  NOT NULL,
    source_type           VARCHAR(40)   NOT NULL,
    source_id             BIGINT        NULL,
    accounting_date       DATE          NOT NULL,
    description           VARCHAR(300)  NOT NULL,
    status                VARCHAR(15)   NOT NULL DEFAULT 'POSTED',
    reversal_of_entry_id  BIGINT        NULL,
    created_by_user_id    BIGINT        NOT NULL,
    posted_by_user_id     BIGINT        NOT NULL,
    posted_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_journal_entries_id_tenant (id, tenant_id),
    UNIQUE KEY uq_journal_entries_posting_key (tenant_id, posting_key),
    KEY ix_journal_entries_tenant_date (tenant_id, accounting_date, id),
    KEY ix_journal_entries_source (tenant_id, source_type, source_id),
    CONSTRAINT fk_journal_entries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_journal_entries_created_by FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES users (id, tenant_id),
    CONSTRAINT fk_journal_entries_posted_by FOREIGN KEY (posted_by_user_id, tenant_id)
        REFERENCES users (id, tenant_id),
    CONSTRAINT fk_journal_entries_reversal FOREIGN KEY (reversal_of_entry_id, tenant_id)
        REFERENCES journal_entries (id, tenant_id),
    CONSTRAINT ck_journal_entries_status CHECK (status IN ('POSTED','REVERSED')),
    CONSTRAINT ck_journal_entries_source CHECK (CHAR_LENGTH(TRIM(source_type)) > 0),
    CONSTRAINT ck_journal_entries_description CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_journal_entries_posting_key CHECK (CHAR_LENGTH(TRIM(posting_key)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE journal_lines (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT        NOT NULL,
    journal_entry_id   BIGINT        NOT NULL,
    line_number        INT           NOT NULL,
    account_id         BIGINT        NOT NULL,
    description        VARCHAR(300)  NULL,
    debit              DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    credit             DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    driver_id          BIGINT        NULL,
    equipment_id       BIGINT        NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_journal_lines_id_tenant (id, tenant_id),
    UNIQUE KEY uq_journal_lines_number (journal_entry_id, tenant_id, line_number),
    KEY ix_journal_lines_account (tenant_id, account_id, journal_entry_id),
    KEY ix_journal_lines_driver (tenant_id, driver_id, journal_entry_id),
    KEY ix_journal_lines_equipment (tenant_id, equipment_id, journal_entry_id),
    CONSTRAINT fk_journal_lines_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_journal_lines_entry FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES journal_entries (id, tenant_id),
    CONSTRAINT fk_journal_lines_account FOREIGN KEY (account_id, tenant_id)
        REFERENCES chart_of_accounts (id, tenant_id),
    CONSTRAINT fk_journal_lines_driver FOREIGN KEY (driver_id, tenant_id)
        REFERENCES drivers (id, tenant_id),
    CONSTRAINT fk_journal_lines_equipment FOREIGN KEY (equipment_id, tenant_id)
        REFERENCES equipment (id, tenant_id),
    CONSTRAINT ck_journal_lines_number CHECK (line_number > 0),
    CONSTRAINT ck_journal_lines_amounts CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
