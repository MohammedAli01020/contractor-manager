# Contractor Manager — Design Documentation

This directory is the **official design reference** for Contractor Manager.

## Working rules

1. The interactive prototype is used for early review; it is not production Flutter code.
2. A screen is considered approved only after the product owner explicitly approves it.
3. Approved screen specifications are recorded here before Flutter implementation.
4. UI must reflect verified backend behavior and accounting rules. Never invent financial figures or imply that estimates are posted transactions.
5. Design work is isolated from application code. No application behavior, `main`, or existing PR is changed by design documentation.
6. Platform Owner Web is a separate responsive product surface and will receive its own specification.

## Directory map

- [Design System](design-system/v1.0.md) — shared visual and interaction rules.
- [Dashboard](dashboard/dashboard.md) — dashboard information architecture and behavior.
- [Equipment profile](equipment/equipment.md) — equipment activity, costs, and shift aggregation.
- [Driver account](drivers/driver-account.md) — wages, advances, payments, and balance statement.
- [Expenses and categories](expenses/expenses.md) — expense linking and user-configurable categories.
- [Progress tracker](DESIGN_PROGRESS.md) — screen-by-screen status, decisions, and approvals.

## Status vocabulary

- **Draft** — under design/review.
- **Approved** — explicitly approved by the product owner.
- **Implemented** — implemented in the application and verified.
- **Deferred** — intentionally postponed.

Last updated: 2026-11-03.
