-- Platform Owner MFA is additive; existing owner records and passwords are preserved.
ALTER TABLE platform_owners
    ADD COLUMN totp_secret VARCHAR(64) NULL,
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_setup_expires_at DATETIME(6) NULL,
    ADD COLUMN totp_enabled_at DATETIME(6) NULL;

-- Recovery credentials will be stored as hashes and consumed once.
CREATE TABLE platform_owner_recovery_codes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id    BIGINT       NOT NULL,
    code_hash   VARCHAR(64)  NOT NULL,
    used_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_platform_owner_recovery_code (owner_id, code_hash),
    KEY ix_platform_owner_recovery_unused (owner_id, used_at),
    CONSTRAINT fk_platform_owner_recovery_owner
        FOREIGN KEY (owner_id) REFERENCES platform_owners (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
