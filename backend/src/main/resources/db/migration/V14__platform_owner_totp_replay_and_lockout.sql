ALTER TABLE platform_owners
    MODIFY COLUMN totp_secret VARCHAR(128) NULL,
    ADD COLUMN totp_last_used_step BIGINT NULL,
    ADD COLUMN totp_failed_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN totp_locked_until DATETIME(6) NULL;
