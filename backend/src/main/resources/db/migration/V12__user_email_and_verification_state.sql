-- Existing accounts remain usable; recovery requires an explicitly verified email.
ALTER TABLE users
    ADD COLUMN email VARCHAR(254) NULL,
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
