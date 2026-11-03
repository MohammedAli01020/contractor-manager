# Inventory and Warehouses — Design Specification

**Status:** Draft / product direction under review  
**Platform:** Flutter mobile app  
**Design baseline:** [Design System v1.0](../design-system/v1.0.md)

## 1. Purpose

Track spare parts, consumables, and materials across one or more company warehouses, while keeping purchasing, stock movement, consumption, and accounting recognition distinct.

## 2. Multiple warehouses

- Support multiple warehouses per tenant from the initial product model.
- A new company may start with one default warehouse; creating more is optional.
- Example locations include a central store and a project/site store.
- Every stock balance is specific to an item and warehouse.
- Warehouse names and operational details may be edited with permission.
- A warehouse with movement history is deactivated/closed rather than hard-deleted.
- Users must have permissions scoped appropriately for viewing, receiving, issuing, transferring, and adjusting stock.

## 3. Item catalogue

Each item should have:
- Unique tenant-scoped item code/SKU where used.
- Name and optional description.
- Category configurable by the tenant.
- Unit of measure (piece, litre, kilogram, metre, etc.).
- Optional barcode/reference and minimum-stock threshold.
- Active/inactive status.

Do not allow a unit-of-measure change to silently reinterpret historical quantities. Items referenced by stock movements are deactivated rather than deleted.

## 4. Main inventory screen

Show:
- Warehouse selector and item search.
- Quantity on hand per warehouse.
- Optional available/reserved quantity only if reservation workflows exist.
- Stock valuation only when a verified costing method and complete cost data are available.
- Low-stock warnings based on configured minimums.
- Recent receipts, issues, transfers, returns, and adjustments.
- Quick actions: receive, issue, transfer, stocktake, and item details.

Do not present sample values as real inventory balances.

## 5. Stock movement workflows

### Purchase and receipt
- Record purchase invoice/order details as supported.
- Receive the actual quantity into a selected warehouse.
- Link the receipt to supplier, purchase, invoice, and cash/bank/payable source records where supported.
- Partial receipts must be representable if purchase orders are supported.
- A purchase receipt increases inventory; it is not automatically an operating expense.

### Issue / consumption
An issue may be associated with:
- Equipment only.
- Shift only.
- Equipment and shift together.
- Another authorized purpose or recipient.

The issue reduces the selected warehouse quantity. If linked to both equipment and shift, it remains one stock movement and one consumption-cost event, visible by reference from both records.

### Transfer between warehouses
- Record source warehouse, destination warehouse, item, quantity, date, and reference.
- Decrease source quantity and increase destination quantity.
- A transfer is internal movement, not a purchase, sale, revenue, or expense.
- Prevent transfer to the same warehouse and prevent issuing more than the available quantity.
- If the system later supports in-transit transfers, represent dispatch and receipt as distinct statuses without creating duplicate stock.

### Returns
Where supported, allow supplier returns and returns from a job/equipment to stock. Each return must reference the original receipt/issue where possible and preserve traceability.

### Stocktake and adjustments
- Provide a counted-quantity workflow with reason, actor, timestamp, and approval when required.
- Post only the difference between system quantity and approved physical count.
- Do not silently overwrite stock balances.
- Corrections to posted movements use a reversal/adjustment trail.

## 6. Direct purchase-and-use without a warehouse

A contractor may buy an item and use it immediately without maintaining a store. Support a distinct direct-purchase-and-consumption workflow:
- Capture item, quantity, cost, supplier/payment details, and intended equipment/shift or other use.
- Do not create a fictitious warehouse receipt and issue merely to satisfy the UI.
- Ensure the purchase and consumption are represented coherently and the cost is recognized once under the accounting policy.
- If an item is actually received into a warehouse, use the normal receipt then issue flow instead.

## 7. Costing and accounting boundaries

- Inventory receipt and inventory consumption are separate events.
- Stock issue cost is recognized according to the configured accounting costing policy.
- Do not count the same purchase as a full expense and then count the issue as a second full expense.
- Internal warehouse transfers do not change company profit.
- Supplier payment settles a payable or records the purchase cash flow; it must not create a second inventory cost.
- Estimated equipment cost is not an actual stock movement or posted expense.
- The costing method (for example weighted average or FIFO) must be explicit, consistent, and validated against backend/accounting behavior before UI implementation. This specification does not select or assume the method.
- Negative stock should be blocked by default. Any future exception must be explicit, permission-controlled, and auditable.

## 8. Acceptance checklist

- [ ] Tenant can operate one or multiple warehouses.
- [ ] Item balances are separate by warehouse.
- [ ] Item units and historical quantity semantics are protected.
- [ ] Receiving stock is distinct from consuming stock.
- [ ] Issues can be linked to equipment, shift, both, or another permitted purpose.
- [ ] One issue linked to equipment and shift is counted once.
- [ ] Transfers adjust both warehouse balances without affecting profit.
- [ ] Stocktake posts approved differences with audit history.
- [ ] Direct purchase-and-use works without a fictitious warehouse.
- [ ] Supplier returns and unused-stock returns have traceable workflows where supported.
- [ ] Stock valuation and issue costing match the backend's explicit costing policy.
- [ ] Negative stock is blocked by default.
- [ ] Offline, permission, validation, loading, and error states are specified.
- [ ] RTL and small-screen behavior reviewed on Android.
- [ ] Product owner explicitly approves the screen.
