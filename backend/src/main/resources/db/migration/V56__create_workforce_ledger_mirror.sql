-- Stage 1 of Workforce ledger unification.
-- Keep driver_ledger_entries as the temporary write authority while creating a
-- Workforce-keyed mirror. The new table owns its primary-key sequence; the legacy
-- row ID is retained only in legacy_source_entry_id for traceability. Independent
-- sequences are required before any future write cutover to avoid ID collisions.
-- Do not switch application reads/writes or drop the legacy table in this migration.

CREATE TABLE workforce_ledger_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    workforce_member_id BIGINT NOT NULL,
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
    legacy_source_entry_id BIGINT NOT NULL,
    legacy_driver_id BIGINT NOT NULL,

    earning_shift_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN transaction_type = 'EARNING'
                 AND client_request_key NOT LIKE 'shift-wage-correction-%'
                THEN shift_id
                ELSE NULL
            END
        ) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uq_workforce_ledger_legacy_source (legacy_source_entry_id),
    UNIQUE KEY uq_workforce_ledger_request (tenant_id, client_request_key),
    UNIQUE KEY uq_workforce_ledger_journal (tenant_id, journal_entry_id),
    UNIQUE KEY uq_workforce_ledger_one_shift_earning (tenant_id, earning_shift_id),
    KEY ix_workforce_ledger_statement (tenant_id, workforce_member_id, transaction_date, id),
    KEY ix_workforce_ledger_legacy_driver (tenant_id, legacy_driver_id, transaction_date, id),

    CONSTRAINT fk_workforce_ledger_member
        FOREIGN KEY (workforce_member_id, tenant_id)
        REFERENCES workforce_members (id, tenant_id),
    CONSTRAINT fk_workforce_ledger_shift
        FOREIGN KEY (shift_id, tenant_id)
        REFERENCES shifts (id, tenant_id),
    CONSTRAINT fk_workforce_ledger_journal
        FOREIGN KEY (journal_entry_id, tenant_id)
        REFERENCES journal_entries (id, tenant_id),

    CONSTRAINT chk_workforce_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_workforce_ledger_effect CHECK (
        balance_effect <> 0 OR transaction_type = 'RECOVERABLE_ADVANCE'
    ),
    CONSTRAINT chk_workforce_ledger_type CHECK (
        transaction_type IN (
            'EARNING',
            'PAYMENT',
            'ADVANCE',
            'RECOVERABLE_ADVANCE',
            'ADVANCE_RECOVERY',
            'ADJUSTMENT',
            'CORRECTION_REVERSAL'
        )
    )
) ENGINE=InnoDB;

-- Preserve every existing ledger movement and its original primary key.
-- The join is tenant-scoped; never infer Workforce IDs from numeric ID equality.
INSERT INTO workforce_ledger_entries (
    tenant_id,
    workforce_member_id,
    transaction_type,
    transaction_date,
    amount,
    balance_effect,
    description,
    client_request_key,
    shift_id,
    journal_entry_id,
    created_by_user_id,
    created_at,
    legacy_source_entry_id,
    legacy_driver_id
)
SELECT
    e.tenant_id,
    d.workforce_member_id,
    e.transaction_type,
    e.transaction_date,
    e.amount,
    e.balance_effect,
    e.description,
    e.client_request_key,
    e.shift_id,
    e.journal_entry_id,
    e.created_by_user_id,
    e.created_at,
    e.id,
    e.driver_id
FROM driver_ledger_entries e
LEFT JOIN drivers d
  ON d.id = e.driver_id
 AND d.tenant_id = e.tenant_id
ORDER BY e.id;

-- A missing Driver-to-Workforce mapping must fail the migration via the
-- NOT NULL workforce_member_id constraint; never silently omit legacy rows.

-- From this point, new legacy writes are mirrored transactionally. The legacy
-- table remains the sole application write authority until a later cutover.
CREATE TRIGGER trg_driver_ledger_mirror_workforce
AFTER INSERT ON driver_ledger_entries
FOR EACH ROW
BEGIN
    INSERT INTO workforce_ledger_entries (
        tenant_id,
        workforce_member_id,
        transaction_type,
        transaction_date,
        amount,
        balance_effect,
        description,
        client_request_key,
        shift_id,
        journal_entry_id,
        created_by_user_id,
        created_at,
        legacy_source_entry_id,
        legacy_driver_id
    )
    SELECT
        NEW.tenant_id,
        d.workforce_member_id,
        NEW.transaction_type,
        NEW.transaction_date,
        NEW.amount,
        NEW.balance_effect,
        NEW.description,
        NEW.client_request_key,
        NEW.shift_id,
        NEW.journal_entry_id,
        NEW.created_by_user_id,
        NEW.created_at,
        NEW.id,
        NEW.driver_id
    FROM drivers d
    WHERE d.id = NEW.driver_id
      AND d.tenant_id = NEW.tenant_id;

    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'WORKFORCE_LEDGER_MIRROR_IDENTITY_NOT_FOUND';
    END IF;
END;

-- The Workforce mirror is also append-only, including during the compatibility phase.
CREATE TRIGGER trg_workforce_ledger_prevent_update
BEFORE UPDATE ON workforce_ledger_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'WORKFORCE_LEDGER_IS_APPEND_ONLY';
END;

CREATE TRIGGER trg_workforce_ledger_prevent_delete
BEFORE DELETE ON workforce_ledger_entries
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'WORKFORCE_LEDGER_IS_APPEND_ONLY';
END;
