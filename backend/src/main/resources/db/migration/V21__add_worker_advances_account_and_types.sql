INSERT INTO chart_of_accounts (tenant_id, account_code, name, account_type, parent_account_id, system_managed, active, created_at, updated_at)
SELECT t.id, '1300', 'سلف العاملين القابلة للاسترداد', 'ASSET', NULL, TRUE, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM tenants t
WHERE NOT EXISTS (SELECT 1 FROM chart_of_accounts a WHERE a.tenant_id=t.id AND a.account_code='1300');

ALTER TABLE driver_ledger_entries
    DROP CHECK chk_driver_ledger_effect,
    DROP CHECK chk_driver_ledger_type;
ALTER TABLE driver_ledger_entries
    ADD CONSTRAINT chk_driver_ledger_effect CHECK (
        balance_effect <> 0 OR transaction_type = 'RECOVERABLE_ADVANCE'
    ),
    ADD CONSTRAINT chk_driver_ledger_type CHECK (
        transaction_type IN ('EARNING','PAYMENT','ADVANCE','RECOVERABLE_ADVANCE','ADJUSTMENT','ADVANCE_RECOVERY')
    );