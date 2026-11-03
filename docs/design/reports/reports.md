# Reports and Management Dashboard — Design Specification

**Status:** Draft / product direction under review  
**Platform:** Flutter mobile app  
**Design baseline:** [Design System v1.0](../design-system/v1.0.md)

## 1. Purpose

Provide the contractor with a concise view of business performance and allow every displayed total to be traced to its underlying records. Reports must distinguish profitability, cash flow, balances due, and operational performance.

## 2. Dashboard overview

The dashboard may show, for a clearly selected period:
- Revenue recognized for the period.
- Expenses/costs recognized for the period.
- Net result under the verified accounting definition.
- Cash and bank balances as of a stated date/time.
- Customer receivables.
- Supplier payables.
- Driver balances/payables.
- Inventory value, only when costing data is reliable.

Every metric must show its period or "as of" date. Cash/bank balances are point-in-time balances, not period revenue. Receivables/payables are balances, not additional revenue/expense.

## 3. Report groups

### Profit and loss
- Revenue, direct costs, operating expenses, and net result.
- Period comparison where comparable data exists.
- Drill-down to source postings.
- Must follow accrual/accounting policy and must not equate cash receipts with revenue.

### Cash flow and treasury
- Actual receipts and payments by date and cash/bank account.
- Opening and closing balances for the selected period.
- Internal transfers shown separately and excluded from income/expense totals.
- Unpaid obligations excluded from cash outflow until paid.

### Receivables and payables
- Customer balances due to the company.
- Supplier balances owed by the company.
- Driver payable/receivable balances, with advances and payments separately visible.
- Aging buckets only when due dates and source data support them.
- Avoid double-counting source transactions and their settlement payments.

### Equipment performance
- Revenue attributable to equipment under a verified allocation rule.
- Actual expenses and consumed stock costs.
- Shift count and utilization metrics where supported.
- Net contribution only when all relevant costs and revenue are captured and allocation rules are explicit.
- Estimated costs must be shown separately from actual costs.

### Driver report
- Shift wages earned.
- Advances issued.
- Payments made.
- Remaining balances.
- Shift-level drill-through.

### Inventory report
- Quantity on hand per item and warehouse.
- Receipts, issues, transfers, returns, and adjustments.
- Low-stock list.
- Inventory valuation only when costing method and cost completeness are verified.

### Accounting reports
- Trial balance.
- General ledger.
- Journal entries.
- Balance sheet / financial position.
- These reports must be generated from posted double-entry records and validated accounting mappings, not approximated from dashboard totals.

## 4. Core definitions and distinctions

- **Profitability:** revenue less recognized costs/expenses for a period, according to the accounting basis and policy.
- **Cash position:** actual cash and bank balances at a point in time.
- **Cash flow:** receipts and payments over a period.
- **Receivable:** amount owed to the company; not cash already collected.
- **Payable:** amount the company owes; not necessarily paid yet.
- **Inventory value:** asset value based on the selected and consistently applied inventory costing method.
- **Operational result:** a management metric for a shift/equipment. It must be labelled separately if it differs from statutory/accounting profit.

## 5. Filters and drill-down

Common filters:
- Date range.
- Equipment.
- Driver.
- Customer/project.
- Warehouse/item.
- Account (cashbox/bank).
- Transaction/category where applicable.

Every report total should drill down to source transactions and retain source identity. Filters must be consistent across the report and exported output. Export (PDF/Excel/CSV) is a later implementation detail and should be offered only when the underlying report is verified.

## 6. Dashboard behavior

- Use a compact mobile-first summary with clear metric labels.
- Allow users to select a period for flow/performance metrics.
- Show "as of" date for balances.
- Separate actuals, estimates, and incomplete data.
- Provide useful empty states when a company has not entered enough data.
- Never show prototype figures as customer data.
- Avoid implying profit is final when costs, inventory valuation, or allocations are incomplete.
- Use permissions to hide sensitive financial reports from unauthorized users.

## 7. Data quality and accounting safeguards

- Revenue and expenses come from the approved accounting source of truth.
- Cash and bank balances come from posted account movements.
- Receivables/payables come from outstanding source obligations.
- Driver balances reconcile to shift wages, advances, payments, and approved adjustments.
- Inventory balances and valuation reconcile to stock movements and the selected costing policy.
- Transfers between cash/bank accounts or warehouses do not create income/expense.
- One source transaction shown in multiple reports is counted once in each relevant aggregate.
- Report calculations must be deterministic, period-aware, tenant-isolated, and tested against ledger data.
- A report must indicate when data is incomplete or a metric cannot be calculated reliably.

## 8. Acceptance checklist

- [ ] Dashboard separates profitability, liquidity, and outstanding balances.
- [ ] Period metrics and point-in-time balances are clearly distinguished.
- [ ] Profit-and-loss totals reconcile to posted accounting records.
- [ ] Cash flow reconciles to treasury movements.
- [ ] Receivables/payables reconcile to outstanding source obligations.
- [ ] Driver balances reconcile to wages, advances, and payments.
- [ ] Equipment result uses documented allocation and actual cost rules.
- [ ] Inventory value uses the approved costing method.
- [ ] Trial balance and general ledger derive from double-entry records.
- [ ] Every report supports drill-down to source records.
- [ ] Incomplete data and unavailable calculations are clearly indicated.
- [ ] Permissions and tenant isolation are enforced.
- [ ] RTL and small-screen behavior reviewed on Android.
- [ ] Product owner explicitly approves the screen.
