-- Expense source documents and their payments. Journals remain immutable and are linked
-- back to the source records; source + journal writes are performed in one transaction.
CREATE TABLE expenses (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT        NOT NULL,
    expense_number        VARCHAR(60)   NOT NULL,
    expense_date          DATE          NOT NULL,
    category_code         VARCHAR(40)   NOT NULL,
    description           VARCHAR(300)  NOT NULL,
    supplier_name         VARCHAR(150)  NULL,
    amount                DECIMAL(15,2) NOT NULL,
    expense_account_id    BIGINT        NOT NULL,
    equipment_id          BIGINT        NULL,
    shift_id              BIGINT        NULL,
    journal_entry_id      BIGINT        NOT NULL,
    created_by_user_id    BIGINT        NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_expenses_id_tenant (id, tenant_id),
    UNIQUE KEY uq_expenses_number (tenant_id, expense_number),
    UNIQUE KEY uq_expenses_journal (tenant_id, journal_entry_id),
    KEY ix_expenses_tenant_date (tenant_id, expense_date, id),
    KEY ix_expenses_equipment (tenant_id, equipment_id, expense_date),
    KEY ix_expenses_shift (tenant_id, shift_id),
    CONSTRAINT fk_expenses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_expenses_account FOREIGN KEY (expense_account_id, tenant_id)
        REFERENCES chart_of_accounts (id, tenant_id),
    CONSTRAINT fk_expenses_equipment FOREIGN KEY (equipment_id, tenant_id)
        REFERENCES equipment (id, tenant_id),
    CONSTRAINT fk_expenses_shift FOREIGN KEY (shift_id, tenant_id)
        REFERENCES shifts (id, tenant_id),
    CONSTRAINT fk_expenses_journal FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES journal_entries (id, tenant_id),
    CONSTRAINT fk_expenses_created_by FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES users (id, tenant_id),
    CONSTRAINT ck_expenses_amount CHECK (amount > 0),
    CONSTRAINT ck_expenses_category CHECK (CHAR_LENGTH(TRIM(category_code)) > 0),
    CONSTRAINT ck_expenses_description CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_expenses_number CHECK (CHAR_LENGTH(TRIM(expense_number)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE expense_payments (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT        NOT NULL,
    expense_id            BIGINT        NOT NULL,
    payment_key           VARCHAR(120)  NOT NULL,
    payment_date          DATE          NOT NULL,
    amount                DECIMAL(15,2) NOT NULL,
    payment_account_id    BIGINT        NOT NULL,
    journal_entry_id      BIGINT        NOT NULL,
    created_by_user_id    BIGINT        NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_expense_payments_id_tenant (id, tenant_id),
    UNIQUE KEY uq_expense_payments_key (tenant_id, payment_key),
    UNIQUE KEY uq_expense_payments_journal (tenant_id, journal_entry_id),
    KEY ix_expense_payments_expense (tenant_id, expense_id, payment_date, id),
    CONSTRAINT fk_expense_payments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_expense_payments_expense FOREIGN KEY (expense_id, tenant_id)
        REFERENCES expenses (id, tenant_id),
    CONSTRAINT fk_expense_payments_account FOREIGN KEY (payment_account_id, tenant_id)
        REFERENCES chart_of_accounts (id, tenant_id),
    CONSTRAINT fk_expense_payments_journal FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES journal_entries (id, tenant_id),
    CONSTRAINT fk_expense_payments_created_by FOREIGN KEY (created_by_user_id, tenant_id)
        REFERENCES users (id, tenant_id),
    CONSTRAINT ck_expense_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_expense_payments_key CHECK (CHAR_LENGTH(TRIM(payment_key)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
