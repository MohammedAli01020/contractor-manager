# Workforce Ledger — read-only reconciliation query pack

**Branch:** `feature/wf12c-shift-workforce-identity`  
**Prepared against:** V17, V20–V22, V25, V29–V35 and the transaction-flow audit in section 13.  
**Purpose:** inspect the current driver-keyed ledger before designing the Workforce-native replacement.  
**Safety:** every statement below is a `SELECT`. No DDL, DML, locks, or data repair. Run only against the WF06 test database first. Do not run against production.

## How to run

1. Confirm the connection is `contractor_manager_wf06_test`.
2. Replace the sample tenant filter `8` with the tenant under review. Keep one tenant per run.
3. Run each query separately and save the result sets with the run date and tenant ID.
4. Empty anomaly queries mean only that this particular check found no rows; they do not prove the entire ledger is reconciled.

The schema uses `driver_ledger_entries`, `drivers`, `shifts`, `journal_entries`, `journal_lines`, `financial_idempotency_keys`, and `shift_corrections`. The legacy ledger remains the current financial source of truth.

---

## Q1 — ledger volume and signed movement by transaction type

```sql
SELECT
    tenant_id,
    transaction_type,
    COUNT(*) AS entry_count,
    SUM(amount) AS gross_amount,
    SUM(balance_effect) AS payable_balance_effect,
    MIN(transaction_date) AS first_transaction_date,
    MAX(transaction_date) AS last_transaction_date
FROM driver_ledger_entries
WHERE tenant_id = 8
GROUP BY tenant_id, transaction_type
ORDER BY transaction_type;
```

Use this to inventory all actual transaction types, especially `ADJUSTMENT`, `RECOVERABLE_ADVANCE`, `ADVANCE_RECOVERY`, and correction movements.

## Q2 — payable and recoverable balances per driver

```sql
SELECT
    d.tenant_id,
    d.id AS driver_id,
    d.workforce_member_id,
    d.name AS driver_name,
    COALESCE(SUM(e.balance_effect), 0) AS payable_balance,
    COALESCE(SUM(CASE
        WHEN e.transaction_type = 'RECOVERABLE_ADVANCE' THEN e.amount
        WHEN e.transaction_type = 'ADVANCE_RECOVERY' THEN -e.amount
        ELSE 0
    END), 0) AS recoverable_advance_outstanding,
    COUNT(e.id) AS ledger_entry_count
FROM drivers d
LEFT JOIN driver_ledger_entries e
       ON e.tenant_id = d.tenant_id
      AND e.driver_id = d.id
WHERE d.tenant_id = 8
GROUP BY d.tenant_id, d.id, d.workforce_member_id, d.name
ORDER BY d.id;
```

Payable is the sum of `balance_effect`. Recoverable advances are deliberately calculated separately; do not combine them into payable.

## Q3 — missing or mismatched ledger references

These checks should normally be prevented by foreign keys, but they are retained as explicit reconciliation evidence and can also expose schema drift in a test copy.

```sql
SELECT
    e.id AS ledger_entry_id,
    e.tenant_id,
    e.driver_id,
    e.shift_id,
    e.journal_entry_id,
    e.transaction_type,
    'MISSING_DRIVER' AS issue
FROM driver_ledger_entries e
LEFT JOIN drivers d
       ON d.id = e.driver_id AND d.tenant_id = e.tenant_id
WHERE e.tenant_id = 8 AND d.id IS NULL

UNION ALL

SELECT
    e.id, e.tenant_id, e.driver_id, e.shift_id, e.journal_entry_id,
    e.transaction_type, 'MISSING_JOURNAL'
FROM driver_ledger_entries e
LEFT JOIN journal_entries j
       ON j.id = e.journal_entry_id AND j.tenant_id = e.tenant_id
WHERE e.tenant_id = 8 AND j.id IS NULL

UNION ALL

SELECT
    e.id, e.tenant_id, e.driver_id, e.shift_id, e.journal_entry_id,
    e.transaction_type, 'MISSING_SHIFT'
FROM driver_ledger_entries e
LEFT JOIN shifts s
       ON s.id = e.shift_id AND s.tenant_id = e.tenant_id
WHERE e.tenant_id = 8
  AND e.shift_id IS NOT NULL
  AND s.id IS NULL

UNION ALL

SELECT
    e.id, e.tenant_id, e.driver_id, e.shift_id, e.journal_entry_id,
    e.transaction_type, 'SHIFT_DRIVER_MISMATCH'
FROM driver_ledger_entries e
JOIN shifts s
  ON s.id = e.shift_id AND s.tenant_id = e.tenant_id
WHERE e.tenant_id = 8
  AND s.driver_id <> e.driver_id;
```

## Q4 — one original earning per shift

V22/V31 enforce one original earning per shift while allowing a specifically keyed correction replacement. This query is a data-level duplicate check.

```sql
SELECT
    tenant_id,
    shift_id,
    COUNT(*) AS original_earning_count,
    GROUP_CONCAT(id ORDER BY id) AS ledger_entry_ids,
    SUM(amount) AS total_original_earning_amount
FROM driver_ledger_entries
WHERE tenant_id = 8
  AND transaction_type = 'EARNING'
  AND client_request_key NOT LIKE 'shift-wage-correction-%'
GROUP BY tenant_id, shift_id
HAVING COUNT(*) > 1;
```

Also check earnings without a source shift:

```sql
SELECT id, tenant_id, driver_id, transaction_date, amount, client_request_key
FROM driver_ledger_entries
WHERE tenant_id = 8
  AND transaction_type = 'EARNING'
  AND shift_id IS NULL;
```

## Q5 — journal debit/credit balance

Every posted journal should balance to zero. This query reports only unbalanced journals.

```sql
SELECT
    j.id AS journal_entry_id,
    j.tenant_id,
    j.source_type,
    j.source_id,
    j.posting_key,
    j.accounting_date,
    COUNT(l.id) AS line_count,
    SUM(l.debit) AS total_debit,
    SUM(l.credit) AS total_credit,
    SUM(l.debit) - SUM(l.credit) AS imbalance
FROM journal_entries j
JOIN journal_lines l
  ON l.journal_entry_id = j.id AND l.tenant_id = j.tenant_id
WHERE j.tenant_id = 8
GROUP BY j.id, j.tenant_id, j.source_type, j.source_id, j.posting_key, j.accounting_date
HAVING SUM(l.debit) <> SUM(l.credit);
```

A journal with no lines is also an exception:

```sql
SELECT j.id, j.tenant_id, j.source_type, j.source_id, j.posting_key
FROM journal_entries j
LEFT JOIN journal_lines l
       ON l.journal_entry_id = j.id AND l.tenant_id = j.tenant_id
WHERE j.tenant_id = 8
GROUP BY j.id, j.tenant_id, j.source_type, j.source_id, j.posting_key
HAVING COUNT(l.id) = 0;
```

## Q6 — ledger movement versus driver-dimension journal movement

For a driver-dimension line, `credit - debit` is the signed journal movement on that dimension. This query intentionally reports every account separately; do not assume all driver-tagged lines belong to the payable control account.

```sql
SELECT
    e.id AS ledger_entry_id,
    e.transaction_type,
    e.amount,
    e.balance_effect,
    e.journal_entry_id,
    l.id AS journal_line_id,
    l.account_id,
    a.account_code,
    a.name AS account_name,
    l.debit,
    l.credit,
    (l.credit - l.debit) AS signed_journal_movement
FROM driver_ledger_entries e
LEFT JOIN journal_lines l
       ON l.tenant_id = e.tenant_id
      AND l.journal_entry_id = e.journal_entry_id
      AND l.driver_id = e.driver_id
LEFT JOIN chart_of_accounts a
       ON a.tenant_id = l.tenant_id AND a.id = l.account_id
WHERE e.tenant_id = 8
ORDER BY e.id, l.line_number;
```

Review the account mapping and transaction semantics before defining an automated equality rule. Recoverable advances have zero payable `balance_effect` but still create a real asset journal movement.

## Q7 — ADJUSTMENT inventory with journal context

```sql
SELECT
    e.id,
    e.driver_id,
    d.workforce_member_id,
    d.name AS driver_name,
    e.transaction_date,
    e.amount,
    e.balance_effect,
    e.description,
    e.client_request_key,
    e.shift_id,
    e.journal_entry_id,
    j.source_type,
    j.source_id,
    j.posting_key
FROM driver_ledger_entries e
JOIN drivers d
  ON d.id = e.driver_id AND d.tenant_id = e.tenant_id
LEFT JOIN journal_entries j
  ON j.id = e.journal_entry_id AND j.tenant_id = e.tenant_id
WHERE e.tenant_id = 8
  AND e.transaction_type = 'ADJUSTMENT'
ORDER BY e.transaction_date, e.id;
```

Do not remove or remap these rows until each one has a documented business meaning and authorization/source.

## Q8 — idempotency reservation inventory

The reservation key is unique by `(tenant_id, operation_scope, request_key)`; ledger rows store `client_request_key` but do not store `operation_scope`. Therefore a key-only join is not proof that a reservation belongs to a particular ledger operation. First inventory scopes and fingerprints:

```sql
SELECT
    tenant_id,
    operation_scope,
    COUNT(*) AS reservation_count,
    MIN(created_at) AS first_reserved_at,
    MAX(created_at) AS last_reserved_at
FROM financial_idempotency_keys
WHERE tenant_id = 8
GROUP BY tenant_id, operation_scope
ORDER BY operation_scope;
```

Show same request keys appearing under multiple scopes (not necessarily an error because scope is part of the primary key):

```sql
SELECT
    tenant_id,
    request_key,
    COUNT(*) AS scope_count,
    GROUP_CONCAT(operation_scope ORDER BY operation_scope) AS scopes
FROM financial_idempotency_keys
WHERE tenant_id = 8
GROUP BY tenant_id, request_key
HAVING COUNT(*) > 1;
```

Compare reservation keys to ledger keys only as a candidate investigation list; legacy ledger rows may legitimately predate reservation records:

```sql
SELECT
    e.id AS ledger_entry_id,
    e.transaction_type,
    e.client_request_key,
    CASE WHEN k.request_key IS NULL THEN 'NO_KEY_MATCH_FOUND' ELSE 'KEY_MATCH_EXISTS_SCOPE_UNVERIFIED' END AS review_status
FROM driver_ledger_entries e
LEFT JOIN financial_idempotency_keys k
       ON k.tenant_id = e.tenant_id
      AND k.request_key = e.client_request_key
WHERE e.tenant_id = 8
ORDER BY e.id;
```

Do not classify `NO_KEY_MATCH_FOUND` as corruption until the exact operation-scope mapping and legacy behavior are checked in the service code.

## Q9 — shift correction chain integrity

The audit table stores exact IDs for the original earning, reversal, and replacement. This query reports APPLYING/APPLIED correction rows whose referenced financial records do not all match.

```sql
SELECT
    c.id AS correction_id,
    c.shift_id,
    c.status,
    c.old_driver_id,
    c.new_driver_id,
    c.old_driver_wage,
    c.new_driver_wage,
    c.old_earning_ledger_entry_id,
    c.reversed_earning_ledger_entry_id,
    c.replacement_earning_ledger_entry_id,
    c.old_wage_journal_entry_id,
    c.wage_reversal_journal_entry_id,
    c.replacement_wage_journal_entry_id,
    CASE WHEN old_e.id IS NULL THEN 1 ELSE 0 END AS missing_old_earning,
    CASE WHEN rev_e.id IS NULL THEN 1 ELSE 0 END AS missing_reversal,
    CASE WHEN new_e.id IS NULL THEN 1 ELSE 0 END AS missing_replacement,
    CASE WHEN old_j.id IS NULL THEN 1 ELSE 0 END AS missing_old_journal,
    CASE WHEN rev_j.id IS NULL THEN 1 ELSE 0 END AS missing_reversal_journal,
    CASE WHEN new_j.id IS NULL THEN 1 ELSE 0 END AS missing_replacement_journal
FROM shift_corrections c
LEFT JOIN driver_ledger_entries old_e
       ON old_e.id = c.old_earning_ledger_entry_id
      AND old_e.tenant_id = c.tenant_id
      AND old_e.driver_id = c.old_driver_id
      AND old_e.shift_id = c.shift_id
      AND old_e.transaction_type = 'EARNING'
      AND old_e.journal_entry_id = c.old_wage_journal_entry_id
LEFT JOIN driver_ledger_entries rev_e
       ON rev_e.id = c.reversed_earning_ledger_entry_id
      AND rev_e.tenant_id = c.tenant_id
      AND rev_e.driver_id = c.old_driver_id
      AND rev_e.shift_id = c.shift_id
      AND rev_e.transaction_type = 'CORRECTION_REVERSAL'
      AND rev_e.journal_entry_id = c.wage_reversal_journal_entry_id
      AND rev_e.amount = c.old_driver_wage
      AND rev_e.balance_effect = -c.old_driver_wage
LEFT JOIN driver_ledger_entries new_e
       ON new_e.id = c.replacement_earning_ledger_entry_id
      AND new_e.tenant_id = c.tenant_id
      AND new_e.driver_id = c.new_driver_id
      AND new_e.shift_id = c.shift_id
      AND new_e.transaction_type = 'EARNING'
      AND new_e.journal_entry_id = c.replacement_wage_journal_entry_id
      AND new_e.amount = c.new_driver_wage
      AND new_e.balance_effect = c.new_driver_wage
LEFT JOIN journal_entries old_j
       ON old_j.id = c.old_wage_journal_entry_id
      AND old_j.tenant_id = c.tenant_id
LEFT JOIN journal_entries rev_j
       ON rev_j.id = c.wage_reversal_journal_entry_id
      AND rev_j.tenant_id = c.tenant_id
      AND rev_j.source_type = 'SHIFT_WAGE_CORRECTION_REVERSAL'
      AND rev_j.source_id = c.id
      AND rev_j.reversal_of_entry_id = c.old_wage_journal_entry_id
      AND rev_j.posting_key = CONCAT('shift-wage-correction-reversal-', c.id)
      AND rev_j.status = 'POSTED'
LEFT JOIN journal_entries new_j
       ON new_j.id = c.replacement_wage_journal_entry_id
      AND new_j.tenant_id = c.tenant_id
      AND new_j.source_type = 'SHIFT_WAGE_CORRECTION'
      AND new_j.source_id = c.id
      AND new_j.posting_key = CONCAT('shift-wage-correction-', c.id)
      AND new_j.status = 'POSTED'
WHERE c.tenant_id = 8
  AND (
      old_e.id IS NULL OR rev_e.id IS NULL OR new_e.id IS NULL
      OR old_j.id IS NULL OR rev_j.id IS NULL OR new_j.id IS NULL
  )
ORDER BY c.id;
```

This is a focused reference check. V34/V35 triggers enforce additional snapshot, amount, date, and state conditions; review the full correction row and shift snapshot before treating any result as a confirmed defect.

## Q10 — completed shifts and earning linkage

```sql
SELECT
    s.id AS shift_id,
    s.driver_id,
    s.workforce_member_id,
    s.status,
    s.shift_date,
    s.driver_wage,
    s.driver_wage_journal_entry_id,
    COUNT(e.id) AS earning_rows,
    SUM(e.amount) AS earning_amount,
    SUM(e.balance_effect) AS earning_effect
FROM shifts s
LEFT JOIN driver_ledger_entries e
       ON e.tenant_id = s.tenant_id
      AND e.shift_id = s.id
      AND e.transaction_type = 'EARNING'
WHERE s.tenant_id = 8
  AND s.status = 'COMPLETED'
GROUP BY
    s.id, s.driver_id, s.workforce_member_id, s.status,
    s.shift_date, s.driver_wage, s.driver_wage_journal_entry_id
ORDER BY s.id;
```

A correction can create a reversal and a replacement earning for one shift. Interpret the original earning count together with Q4 and Q9; do not compare the raw sum to the current shift wage without accounting for correction history.

---

## Interpretation and stop conditions

- Preserve every row and journal; this pack is diagnostic only.
- Do not use `SUM(balance_effect)` as a replacement for transaction history.
- Do not infer account semantics solely from account codes. Inspect the chart and actual journal lines per tenant.
- Do not assume all tenant IDs share the same data quality as WF06.
- Any orphan, duplicate, unbalanced journal, or correction mismatch must be investigated against source records before proposing repair.
- The next design deliverable remains the Workforce Ledger schema/API and journal-dimension proposal. No backfill, dual-write, API cutover, deletion, or production migration is authorized by these queries.
