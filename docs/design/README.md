# Contractor Manager — Design Documentation

This directory is the **official design reference** for Contractor Manager.

## Working rules

1. Interactive prototypes are for early review, not production Flutter code.
2. A screen is approved only after explicit product-owner approval.
3. Approved specifications are recorded here before Flutter implementation.
4. UI must reflect verified backend behavior and accounting rules.
5. Design work is isolated from application code; it does not modify `main` or merge existing PRs.
6. Platform Owner Web is a separate responsive product surface.

## Directory map

- [Design System](design-system/v1.0.md) — shared visual and interaction rules.
- [Dashboard](dashboard/dashboard.md) — dashboard information architecture.
- [Equipment profile](equipment/equipment.md) — equipment activity, costs, and shift aggregation.
- [Driver account](drivers/driver-account.md) — wages, advances, payments, and statement.
- [Expenses and categories](expenses/expenses.md) — expense linking and configurable categories.
- [Treasury and banks](treasury/treasury.md) — cashboxes, bank accounts, receipts, payments, and transfers.
- [Inventory and warehouses](inventory/inventory.md) — multiple warehouses, item movements, stocktake, and direct purchase/use.
- [Reports](reports/reports.md) — management dashboard, profitability, cash flow, balances, and accounting reports.
- [Accounting period close](accounting/period-close.md) — monthly/annual close, review checklist, corrections, approvals, and integrity controls.
- [Progress tracker](DESIGN_PROGRESS.md) — screen status, decisions, and approvals.

## Status vocabulary

- **Draft** — under design/review.
- **Approved** — explicitly approved by the product owner.
- **Implemented** — implemented and verified in the application.
- **Deferred** — intentionally postponed.

Last updated: 2026-11-03.
