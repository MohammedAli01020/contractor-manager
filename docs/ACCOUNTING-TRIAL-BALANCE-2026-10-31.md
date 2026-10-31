# Trial Balance Report — 2026-10-31

## Scope
This slice adds a read-only, tenant-scoped trial-balance report over immutable posted journal entries. It does not add or change any Flyway migration and does not close an accounting period.

## API
`GET /api/v1/accounting/reports/trial-balance?from=YYYY-MM-DD&to=YYYY-MM-DD`

- Both dates are required; start must not be after end.
- Dates are accounting dates and both boundaries are inclusive.
- The opening balance is the net of all posted journal lines strictly before `from`.
- Period debit/credit are gross movements posted between `from` and `to`, inclusive.
- Closing balance is opening balance plus period debits minus period credits.
- Every tenant chart account is returned, including zero-balance and inactive accounts, so historic accounts remain visible.
- Rows are sorted by account code then account ID.
- Debit/credit balance columns are normalized per account: a positive net is shown as debit; a negative net is shown as credit. Period movement columns remain gross debit/credit totals.
- The response exposes opening, period and closing totals and a `balanced` flag based on closing debit total equaling closing credit total.

## Safety and performance
- Tenant ID is taken only from the authenticated principal; it is never accepted from the request.
- The SQL query applies tenant constraints to both account and journal-line/entry joins.
- Only entries with `POSTED` status and accounting date on or before the requested end date contribute.
- Aggregation is performed in MySQL rather than loading journal lines into application memory.
- Existing indexes on `journal_entries(tenant_id, accounting_date, id)` and `journal_lines(tenant_id, account_id, journal_entry_id)` support the report. No schema migration is required.
- Viewer, operator and admin roles may read the report. No write operation is exposed.

## Verification
- MySQL/Testcontainers integration tests cover opening/period/closing balances, both inclusive date boundaries, excluding entries after the end date, zero-balance and inactive accounts, tenant isolation, invalid date order and viewer/authentication permissions.
- **Verified 2026-10-31:** push run [41229634484](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41229634484) and PR run [41229638627](https://github.com/MohammedAli01020/contractor-manager/actions/runs/41229638627) succeeded on code commit `1c4b42a84b2d72e1647803af8b6dd223ca49fa8f`. Surefire artifact: 30 XML reports, 175 tests, 0 failures, 0 errors and 0 skipped. The trial-balance suite executed all 7 MySQL integration tests.
- CI requires the trial-balance integration suite to execute at least seven tests with no skips, failures or errors.
- This report is a financial read model, not a reconciliation/closing workflow. Monthly close, account statements and controlled period locking remain separate future work.
