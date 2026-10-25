-- Platform identities are separate from tenant-scoped users.
CREATE TABLE platform_owners (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(60)  NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_platform_owners_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Composite key supports a tenant-scoped FK for activation credentials.
ALTER TABLE users
    ADD UNIQUE KEY uq_users_id_tenant (id, tenant_id),
    ADD COLUMN password_activated BOOLEAN NOT NULL DEFAULT TRUE;

-- One-time activation credentials are stored as SHA-256 hashes only.
CREATE TABLE tenant_admin_activation_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    consumed_at DATETIME(6)  NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenant_admin_activation_hash (token_hash),
    KEY ix_tenant_admin_activation_user (user_id, tenant_id),
    CONSTRAINT fk_tenant_admin_activation_user_tenant
        FOREIGN KEY (user_id, tenant_id) REFERENCES users (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
