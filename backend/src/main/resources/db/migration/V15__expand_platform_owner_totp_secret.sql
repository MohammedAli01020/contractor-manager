-- Encrypted TOTP values include a nonce, authentication tag, Base64 encoding and version prefix.
-- Keep enough space for the encrypted representation, not only the plaintext seed.
ALTER TABLE platform_owners
    MODIFY COLUMN totp_secret VARCHAR(128) NULL;
