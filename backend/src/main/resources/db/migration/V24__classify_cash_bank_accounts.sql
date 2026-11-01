-- Classify liquidity accounts explicitly instead of relying on account-code magic values.
-- Existing tenants retain the current 1000/1010 behavior after the backfill.
ALTER TABLE chart_of_accounts
    ADD COLUMN cash_bank_account BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE chart_of_accounts
SET cash_bank_account = TRUE
WHERE account_type = 'ASSET'
  AND account_code IN ('1000', '1010');

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT ck_chart_accounts_cash_bank_type
    CHECK (cash_bank_account = FALSE OR account_type = 'ASSET');

CREATE INDEX ix_chart_accounts_tenant_cash_bank
    ON chart_of_accounts (tenant_id, cash_bank_account, active, id);
