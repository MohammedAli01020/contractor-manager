-- Serialize retries by tenant, operation scope and client-provided key.
-- The reservation row is inserted and locked in the same transaction as the financial write.
CREATE TABLE financial_idempotency_keys (
    tenant_id BIGINT NOT NULL,
    operation_scope VARCHAR(40) NOT NULL,
    request_key VARCHAR(120) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tenant_id, operation_scope, request_key),
    KEY ix_financial_idempotency_created_at (created_at),
    CONSTRAINT fk_financial_idempotency_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_financial_idempotency_scope
        CHECK (CHAR_LENGTH(TRIM(operation_scope)) > 0),
    CONSTRAINT ck_financial_idempotency_key
        CHECK (CHAR_LENGTH(TRIM(request_key)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
