# Approval Workflow and Permissions

**Status: Approved product design direction**  
**Approval date: 2026-11-04**  
**Scope: Product specification only; not application implementation.**

## 1. Product principles

- Role permissions answer **what a user may do**.
- Approval policies answer **when an otherwise permitted operation requires another person's approval**.
- Do not route every operation through approval. Routine, low-risk work should remain direct when the user's permissions allow it.
- Backend authorization is authoritative. Hiding a Flutter action is not a security control.
- Approval records, financial execution, and accounting posting are distinct concepts. Approval alone must not imply that money moved or a journal entry was posted.
- Policies and thresholds are tenant-scoped and configurable, with safe defaults.

## 2. Initial role templates

| Role | Default responsibility |
|---|---|
| Company owner | Company-wide authority, policy configuration, exceptional decisions |
| Company manager | Daily operations and delegated approvals |
| Accountant | Accounting review, statements, journal and period workflows within assigned authority |
| Operations supervisor | Shifts, equipment and operational records |
| Cashier / treasury custodian | Receipts, payments and cash custody within limits |
| Storekeeper | Stock receipts, issues, transfers and stocktake preparation |
| Driver | Own permitted shifts and account information; submit review requests |

These are templates, not hard-coded universal permissions. A tenant may grant or remove granular permissions while preserving protected system rules.

## 3. Permission dimensions

Permissions should be granular by action and resource, for example:

- View, create, edit draft, submit, approve, reject, execute, post, correct, export, configure.
- Resource scope: whole company, assigned branch, project, work site, warehouse, equipment, or own driver profile, where applicable.
- Sensitive financial/accounting actions require explicit permissions; they must not be inferred from a broad view or create permission.
- A user cannot grant themselves privileges or expand their own approval authority.
- Tenant isolation is mandatory for every permission check and query.

## 4. Approval policy

Each operation type has its own configurable policy. The company can define:

- Whether approval is required.
- Applicable amount thresholds and currency.
- Which role or explicitly authorized users can approve.
- Whether one or more approval levels are required.
- Whether the requester may approve their own request. For sensitive actions, default to **no** (maker-checker separation).
- Whether approval is required by amount, operation type, resource scope, or exception condition.

Illustrative thresholds only (not product defaults): an expense up to EGP 1,000 may be directly authorized for a delegated supervisor; EGP 1,001–5,000 may require a manager; above EGP 5,000 may require the owner. Actual thresholds are configured by each company and may differ by operation type. An expense threshold must not automatically govern advances, equipment purchases, stock adjustments, or period close.

## 5. Initial operation examples

| Operation | Proposed default behavior |
|---|---|
| View records / add ordinary notes | Direct, subject to view/edit permissions |
| Create an expense draft | Direct if create permission exists |
| Submit an expense above the user's authority | Approval required by the expense policy |
| Record a shift | Direct if create permission exists |
| Approve a completed shift | Approval only when required by role or configured policy |
| Correct a completed/old shift | Controlled correction request, reason and audit trail; approval by policy |
| Issue a driver advance | Approval according to advance policy; not an operating expense by default |
| Settle a custody/advance discrepancy | Review and approval according to discrepancy policy |
| Execute a payment | Requires payment/execution authority and applicable approval; approval does not itself move funds |
| Post a journal entry | Explicit accounting permission and validation; posted entries are corrected through controlled reversal/adjustment, not silent editing |
| Stocktake adjustment | Reason, stock validation and approval according to quantity/value/risk policy |
| Close a financial period | Restricted close permission and required review checklist |
| Reopen a closed period | Exceptional permission, mandatory reason and auditable approval |

These are workflow defaults to refine against the backend and accounting rules before implementation.

## 6. Request lifecycle

Approval request lifecycle:

1. Draft
2. Pending Approval
3. Approved or Rejected
4. Executed / Posted, where the operation requires a separate execution or posting step
5. Cancelled, when cancellation is permitted

A request may also be returned for changes if the workflow supports that action. Keep approval status separate from the underlying business transaction status and journal posting status. Rejection requires a reason. Cancellation and resubmission preserve history rather than erasing prior decisions.

## 7. Separation of duties

- For sensitive workflows, the requester must not approve their own request by default.
- The approver must hold the relevant approval permission and scope at decision time.
- Approval does not grant payment, journal-posting, stock-adjustment, or period-close execution authority unless separately granted.
- A user may prepare a transaction, another may approve it, and a properly authorized user/process may execute or post it.
- Emergency/owner override, if later supported, must require explicit elevated permission, a reason, and immutable audit evidence.

## 8. User experience

### My approval requests
- Show requests created by the current user.
- Display operation, amount where relevant, target resource, submission time, current status, approver/decision, and reason.
- Provide a deep link to the source record, subject to current access permissions.
- Show rejected/returned requests and preserve their history.

### Approval inbox
- Show pending requests the current user is authorized to review.
- Filter by operation type, date, amount, requester, branch/project/resource, and urgency where relevant.
- Present source details and validation warnings before the decision.
- Provide approve/reject actions; rejection requires a reason.
- Avoid implying that approval has already executed a payment or posted a journal entry.

## 9. Audit and security

Record at minimum: tenant, request ID, source transaction, operation type, requester, submitted timestamp, policy/version evaluated, approver, decision, timestamp, rejection/override reason, execution/posting reference, and relevant before/after values for corrections.

Audit records are append-only from the product perspective. Re-check authorization server-side at submission, decision, execution, and posting. Protect against duplicate submissions and repeated execution with stable idempotency keys and transactional backend controls. Do not expose cross-tenant or out-of-scope records in inboxes, search, notifications, or deep links.

## 10. Delivery scope

### Initial release
- Role templates with granular action permissions.
- Tenant-scoped permission assignment.
- Audit trail for permission changes and sensitive operations.
- Configurable approval requirement and amount thresholds for selected workflows.
- Single-level approval inbox and requester tracking.
- Maker-checker separation for selected sensitive operations.
- Explicit separation of approval from execution and accounting posting.

### Later phases
- Multi-level and conditional approval chains.
- Advanced branch/project/resource-specific policy builders.
- Delegation, absence coverage and escalation timers.
- Advanced analytics and policy simulation.

## 11. Implementation guardrails

This document approves the product design direction, not a specific backend schema or production implementation. Before coding, map the policy to existing users/roles/permissions, tenant model, source transactions, journal posting, and period-close behavior. Avoid duplicating existing authorization mechanisms. Validate concurrency, idempotency, audit integrity, and mobile usability.

