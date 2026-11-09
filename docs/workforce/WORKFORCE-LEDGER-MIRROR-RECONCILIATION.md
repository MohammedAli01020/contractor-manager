# Workforce Ledger Mirror — Read-only Reconciliation Pack

**Branch:** `feature/workforce-ledger-unification`  
**Schema:** V56 compatibility mirror  
**Safety:** Every query in this document is read-only. Run against an isolated MySQL 8 test database first. Never use these queries as permission to cut over or drop the legacy ledger.

## Preconditions

1. Confirm the selected database and tenant before running any query.
2. Keep the legacy ledger as the current write authority.
3. Do not update or delete ledger or journal rows to make totals match.
4. An empty anomaly result proves only that the specific query found no anomaly.

## 1. Row counts and transaction types

```sql
SELECT 'legacy' AS ledger_name, tenant_id, transaction_type,
       COUNT(*) AS row_count, SUM(amount) AS gross_amount,
       SUM(balance_effect) AS payable_effect
FROM driver_ledger_entries
GROUP BY tenant_id, transaction_type
UNION ALL
SELECT 'workforce', tenant_id, transaction_type,
       COUNT(*), SUM(amount), SUM(balance_effect)
FROM workforce_ledger_entries
GROUP BY tenant_id, transaction_type
ORDER BY tenant_id, transaction_type, ledger_name;
```

Compare the two result sets by tenant and transaction type. Counts and sums must match before any read cutover.

## 2. Missing or ambiguous Driver-to-Workforce mappings

```sql
SELECT e.id AS legacy_entry_id, e.tenant_id, e.driver_id
FROM driver_ledger_entries e
LEFT JOIN drivers d
  ON d.id = e.driver_id AND d.tenant_id = e.tenant_id
LEFT JOIN workforce_members wm
  ON wm.id = d.workforce_member_id AND wm.tenant_id = d.tenant_id
WHERE d.id IS NULL OR wm.id IS NULL;
```

Expected result: no rows. Never map by matching numeric IDs, names, or phone numbers.

## 3. Missing mirror rows and duplicate source references

```sql
SELECT e.id AS legacy_entry_id, e.tenant_id, e.driver_id
FROM driver_ledger_entries e
LEFT JOIN workforce_ledger_entries w
  ON w.tenant_id = e.tenant_id
 AND w.legacy_source_entry_id = e.id
WHERE w.id IS NULL;

SELECT tenant_id, legacy_source_entry_id, COUNT(*) AS copies
FROM workforce_ledger_entries
GROUP BY tenant_id, legacy_source_entry_id
HAVING COUNT(*) <> 1;
```

Expected result: both queries return no rows.

## 4. Field-by-field mirror mismatches

```sql
SELECT
    e.id AS legacy_entry_id,
    w.id AS workforce_entry_id,
    e.tenant_id,
    e.driver_id,
    d.workforce_member_id,
    e.transaction_type AS legacy_type,
    w.transaction_type AS workforce_type,
    e.transaction_date AS legacy_date,
    w.transaction_date AS workforce_date,
    e.amount AS legacy_amount,
    w.amount AS workforce_amount,
    e.balance_effect AS legacy_effect,
    w.balance_effect AS workforce_effect,
    e.client_request_key AS legacy_request_key,
    w.client_request_key AS workforce_request_key,
    e.shift_id AS legacy_shift_id,
    w.shift_id AS workforce_shift_id,
    e.journal_entry_id AS legacy_journal_id,
    w.journal_entry_id AS workforce_journal_id
FROM driver_ledger_entries e
JOIN drivers d
  ON d.id = e.driver_id AND d.tenant_id = e.tenant_id
LEFT JOIN workforce_ledger_entries w
  ON w.tenant_id = e.tenant_id
 AND w.legacy_source_entry_id = e.id
WHERE w.id IS NULL
   OR w.workforce_member_id <> d.workforce_member_id
   OR w.legacy_driver_id <> e.driver_id
   OR w.transaction_type <> e.transaction_type
   OR w.transaction_date <> e.transaction_date
   OR w.amount <> e.amount
   OR w.balance_effect <> e.balance_effect
   OR w.description <> e.description
   OR w.client_request_key <> e.client_request_key
   OR NOT (w.shift_id <=> e.shift_id)
   OR w.journal_entry_id <> e.journal_entry_id
   OR w.created_by_user_id <> e.created_by_user_id
   OR w.created_at <> e.created_at
   OR w.id <> e.id;
```

Expected result: no rows. This compares exact financial and provenance fields; do not use rounded display values.

## 5. Per-member payable and recoverable balances

```sql
SELECT
    w.tenant_id,
    w.workforce_member_id,
    COUNT(*) AS entry_count,
    SUM(w.balance_effect) AS payable_balance,
    SUM(CASE
        WHEN w.transaction_type = 'RECOVERABLE_ADVANCE' THEN w.amount
        WHEN w.transaction_type = 'ADVANCE_RECOVERY' THEN -w.amount
        ELSE 0
    END) AS recoverable_advance_balance
FROM workforce_ledger_entries w
GROUP BY w.tenant_id, w.workforce_member_id
ORDER BY w.tenant_id, w.workforce_member_id;
```

Compare against the same legacy totals grouped by the tenant-scoped Driver-to-Workforce mapping. Keep payable and recoverable-advance balances separate.

## 6. Correction reference integrity

```sql
SELECT
    c.id AS correction_id,
    c.tenant_id,
    c.shift_id,
    c.status,
    c.old_earning_ledger_entry_id,
    old_w.id AS mirrored_old_entry,
    c.reversed_earning_ledger_entry_id,
    reversal_w.id AS mirrored_reversal_entry,
    c.replacement_earning_ledger_entry_id,
    replacement_w.id AS mirrored_replacement_entry
FROM shift_corrections c
LEFT JOIN workforce_ledger_entries old_w
  ON old_w.tenant_id = c.tenant_id
 AND old_w.legacy_source_entry_id = c.old_earning_ledger_entry_id
LEFT JOIN workforce_ledger_entries reversal_w
  ON reversal_w.tenant_id = c.tenant_id
 AND reversal_w.legacy_source_entry_id = c.reversed_earning_ledger_entry_id
LEFT JOIN workforce_ledger_entries replacement_w
  ON replacement_w.tenant_id = c.tenant_id
 AND replacement_w.legacy_source_entry_id = c.replacement_earning_ledger_entry_id
WHERE (c.old_earning_ledger_entry_id IS NOT NULL AND old_w.id IS NULL)
   OR (c.reversed_earning_ledger_entry_id IS NOT NULL AND reversal_w.id IS NULL)
   OR (c.replacement_earning_ledger_entry_id IS NOT NULL AND replacement_w.id IS NULL);
```

Expected result: no rows for populated references. This query verifies mirror provenance only; it does not replace the full correction-chain and journal validation pack.

## 7. Mirror trigger presence

```sql
SELECT TRIGGER_NAME, EVENT_MANIPULATION, ACTION_TIMING, EVENT_OBJECT_TABLE
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA = DATABASE()
  AND TRIGGER_NAME IN (
      'trg_driver_ledger_mirror_workforce',
      'trg_workforce_ledger_prevent_update',
      'trg_workforce_ledger_prevent_delete'
  )
ORDER BY TRIGGER_NAME;
```

Expected result: exactly three triggers. Confirm their definitions separately with `SHOW CREATE TRIGGER`.

## Stop conditions

Do not proceed to application read/write cutover if any mapping, missing-row, field-mismatch, balance, or correction-reference query reports anomalies. Do not drop the legacy ledger during this stage. A successful mirror comparison is necessary but not sufficient: application consumers, mobile clients, journal dimensions, idempotency, locking, and production backup/restore procedures must also be validated.
