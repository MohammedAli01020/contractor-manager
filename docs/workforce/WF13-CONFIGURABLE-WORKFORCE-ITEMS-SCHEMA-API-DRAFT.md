# WF13 — Configurable Workforce Items: Schema and API Contract Draft

- **Status:** Partially implemented on the working branch; application lifecycle and posting remain design-only and require separate review
- **Date:** 2026-11-09
- **Branch:** `feature/workforce-ledger-unification`
- **Related ADR:** `ADR-WF-13-CONFIGURABLE-WORKFORCE-FINANCIAL-ITEMS.md`
- **Implementation boundary:** The definition catalog and optional compensation-component link now have Flyway migrations V60/V61 and backend CRUD/integration coverage on this branch. The application lifecycle below remains a proposed contract only. No financial writer or ledger posting was changed.

## 1. Design boundary

The catalog is a reusable tenant-owned definition layer. It is not the ledger and it is not a replacement for compensation agreements.

Keep the existing concepts distinct:

- `CompensationAgreement`: effective-dated contractual terms for one Workforce member and optional assignment.
- `CompensationComponent`: agreement-specific rate/term used to resolve contractual compensation.
- `WorkforceFinancialItemDefinition` (implemented on this branch): reusable tenant-owned definition such as Transport Allowance or Meal Allowance.
- `WorkforceFinancialItemApplication` (proposed; not implemented): one application of a definition to one person/period/source, including approval and calculation snapshot.
- Workforce ledger/journal: authoritative posted financial effect, through the separately approved canonical writer boundary.

A definition is not a financial movement. A draft or pending application is not a posted balance movement.

## 2. First-release scope

### Included in the first controlled release

- Tenant-owned definitions for **earning additions** and **deduction proposals**.
- Stable code and editable display name/description.
- Active/inactive state.
- Bounded calculation methods: fixed amount, per hour, per day, per shift, per quantity, and manual amount.
- Optional recurring eligibility for compensation agreements and one-off eligibility.
- Approval policy flags and role authorization.
- Source traceability and immutable application snapshots.
- Mobile-readable statement labels and pending-approval visibility.

### Explicitly deferred

- Arbitrary formula/script execution.
- Percentage calculations until each supported base and its accounting meaning are specified.
- Automated payroll/tax/statutory deduction rules until reviewed by a qualified Egyptian accountant.
- Earned-pay advances, recoverable advances, payments, reimbursements, custody, and settlements as catalog-defined item types. Their existing or future dedicated workflows remain separate.
- Multi-currency posting until the accounting foundation supports it end-to-end.
- Automatic retroactive recalculation of previously approved or posted periods.

## 3. Logical schema and implementation status

Catalog table `workforce_financial_item_definitions` is implemented by V60. The compensation link is implemented by V61. The application table remains provisional; its final columns, indexes, FK ordering, and MySQL constraints must be reviewed against the complete repository before writing a migration.

### 3.1 `workforce_financial_item_definitions` — implemented

One row represents a tenant's reusable item definition. V60 implements tenant-scoped code uniqueness, controlled family/method values, recurring/one-off flags, approval flag, active state, and optimistic versioning. Backend endpoints provide list/get/create/update/deactivate with tenant isolation. The implementation currently exposes `EARNING` and `DEDUCTION` families and bounded methods `FIXED`, `PER_HOUR`, `PER_DAY`, `PER_SHIFT`, `PER_QUANTITY`, `MANUAL`.

| Field | Proposed type | Rule |
|---|---|---|
| `id` | BIGINT | Primary key |
| `tenant_id` | BIGINT | Required tenant owner |
| `item_code` | VARCHAR(60) | Stable, normalized code; unique within tenant |
| `display_name` | VARCHAR(120) | Required tenant-visible label |
| `description` | VARCHAR(500) | Optional explanation |
| `item_family` | VARCHAR(30) | Controlled: `EARNING` or `DEDUCTION` in v1 |
| `active` | BOOLEAN | Deactivation blocks new applications |
| `allow_recurring` | BOOLEAN | May be referenced by a compensation agreement |
| `allow_one_off` | BOOLEAN | May be applied as a one-off item |
| `allowed_calculation_methods` | Controlled representation | Server-validated bounded set; no executable formula |
| `requires_approval` | BOOLEAN | Approval policy flag |
| `default_expense_account_id` | BIGINT NULL | Tenant-scoped account mapping, validated by family/account type |
| `created_by_user_id`, `updated_by_user_id` | BIGINT | Tenant-scoped audit actors |
| `created_at`, `updated_at`, `version` | Timestamp/version | Optimistic concurrency and audit metadata |

Required constraints:
- Unique `(tenant_id, item_code)`.
- Tenant-safe composite FK to tenant and any selected chart account/user.
- Family allow-list; no client-defined family strings.
- Account mapping must be compatible with the item's controlled family.
- No hard delete once referenced; deactivate instead.
- A definition's family and accounting semantics cannot be changed after use. Create a replacement definition if semantics need to change.

### 3.2 Compensation agreement integration — partially implemented

V61 adds nullable `financial_item_definition_id` to `compensation_components` with a tenant-safe composite FK. Existing agreement-local components remain supported. Catalog links currently permit only `PER_HOUR`, `PER_SHIFT`, and `PER_QUANTITY`, mapped to existing resolver bases `HOUR`, `SHIFT`, and `QUANTITY`; `PER_DAY`, `FIXED`, and `MANUAL` are rejected for agreement links until the resolver has matching tested semantics. Do not replace `compensation_components`.

Proposed evolution: add nullable `financial_item_definition_id` to `compensation_components` after a compatibility review.

- Existing components remain valid and retain their current agreement-scoped code/name/rate snapshots.
- A new recurring component may reference a tenant definition whose family is `EARNING` and `allow_recurring=true`.
- The agreement component continues to own the agreed rate, basis, unit, and effective agreement context.
- Definition display-name changes must not rewrite an approved agreement or historical shift snapshot.
- If the tenant has not adopted the catalog, legacy/agreement-local components continue to work during a documented transition.
- Enforce that the definition belongs to the same tenant as the agreement using a composite tenant-safe FK or equivalent database-enforced constraint.

The first catalog integration should not silently change the current resolver behavior. DAY/FIXED support is a separate acceptance item with its own calculation rules and tests.

### 3.3 `workforce_financial_item_applications`

One row represents an application request and its lifecycle. It is the source document for a future ledger posting, not a second balance ledger.

| Field | Proposed type | Rule |
|---|---|---|
| `id` | BIGINT | Primary key |
| `tenant_id` | BIGINT | Required tenant |
| `workforce_member_id` | BIGINT | Canonical person |
| `item_definition_id` | BIGINT | Definition reference |
| `status` | VARCHAR(24) | `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `POSTED`, `REJECTED`, `CANCELLED` |
| `effective_date` | DATE | Business effective date |
| `period_start`, `period_end` | DATE NULL | Optional period, validated as a pair |
| `source_type`, `source_id` | VARCHAR/BIGINT NULL | Shift, attendance, agreement, or other approved source |
| `calculation_method` | VARCHAR(24) | Snapshot of method used |
| `quantity`, `unit_code` | DECIMAL/VARCHAR NULL | Captured calculation inputs |
| `rate`, `base_amount`, `final_amount` | DECIMAL(15,2) | Captured calculation result |
| `currency_code` | CHAR(3) | Must match supported ledger currency |
| `item_code_snapshot`, `item_name_snapshot`, `item_family_snapshot` | Snapshot fields | Preserve historical meaning |
| `reason` | VARCHAR(500) | Required for manual/deduction applications |
| `created_by_user_id`, `approved_by_user_id` | BIGINT | Tenant-scoped actors |
| `approved_at`, `posted_at` | DATETIME(6) NULL | Lifecycle audit |
| `journal_entry_id` | BIGINT NULL | Set only when the canonical posting transaction succeeds |
| `ledger_entry_reference` | BIGINT NULL | Reference to the canonical ledger movement, not an independent balance |
| `client_request_key` | VARCHAR(120) | Tenant-scoped idempotency key |
| `version`, timestamps | Version/timestamps | Concurrency and audit |

Required invariants:
- Tenant-safe FKs for member, definition, actors, journal, and any source entity.
- Positive final amount; direction comes from the controlled item family and posting policy, not a client-supplied signed amount.
- `period_start <= period_end`; both null or both present.
- Approval fields required only in approved/posted lifecycle states.
- Posted applications are immutable. Correction creates a linked reversal/correction application; never edit the posted amount or snapshot.
- Unique idempotency reservation and request fingerprint follow the existing `FinancialIdempotencyService` semantics.
- At most one posting for an application, enforced by a unique journal/ledger association or equivalent invariant.
- No direct client-supplied journal ID or ledger ID.
- Rejected/cancelled applications never affect a financial balance.

The exact relationship between application status and journal posting must be atomic. There must not be a state where an application says POSTED but its journal/ledger movement is missing, or vice versa.

## 4. Calculation contract

The server calculates the final amount from validated source data:

| Method | Inputs | Example |
|---|---|---|
| `FIXED` | Agreed amount per application/period | 250 EGP transport allowance |
| `PER_HOUR` | Approved hours × rate | 8 × 20 EGP |
| `PER_DAY` | Eligible approved days × rate | 5 × 50 EGP |
| `PER_SHIFT` | Eligible completed shifts × rate | 3 × 30 EGP |
| `PER_QUANTITY` | Approved quantity × rate + explicit unit | 12 m³ × 15 EGP |
| `MANUAL` | Entered amount + reason + permission | One-off approved bonus |

Rules:
- Use decimal arithmetic and the application's money/rounding policy.
- Reject negative rates/quantities unless a separately specified business rule requires otherwise; the family determines addition versus deduction.
- Do not accept a precomputed client total when the server can derive it from approved shift/hour/day/quantity facts.
- Persist the inputs, rate, calculation method, rounding result, and final amount.
- Percentage is deliberately excluded from v1 until its base (gross wage, base wage, quantity value, etc.) is unambiguous and snapshot-safe.

## 5. Proposed API contract

All routes are tenant-scoped from authenticated context. The tenant ID is never accepted as an authoritative client parameter.

### Definitions

- `GET /api/v1/workforce/financial-item-definitions` — list tenant definitions with active/family filters.
- `POST /api/v1/workforce/financial-item-definitions` — create definition; ADMIN-only.
- `GET /api/v1/workforce/financial-item-definitions/{definitionId}` — get definition.
- `PUT /api/v1/workforce/financial-item-definitions/{definitionId}` — update mutable metadata/policy with version check; immutable semantic fields protected after first use.
- `POST /api/v1/workforce/financial-item-definitions/{definitionId}/deactivate` — stop future use without deleting history.

### Applications

- `GET /api/v1/workforce/members/{memberId}/financial-items` — list applications and status.
- `POST /api/v1/workforce/members/{memberId}/financial-items` — create a one-off application request; never posts directly unless the approved policy explicitly permits a single atomic approve-and-post path.
- `POST /api/v1/workforce/members/{memberId}/financial-items/{applicationId}/submit` — submit for approval.
- `POST /api/v1/workforce/members/{memberId}/financial-items/{applicationId}/approve` — approve under role and separation-of-duties rules.
- `POST /api/v1/workforce/members/{memberId}/financial-items/{applicationId}/reject` — reject with reason.
- `POST /api/v1/workforce/members/{memberId}/financial-items/{applicationId}/post` — future canonical posting boundary only; enabled after writer transition approval and transactional tests.
- `GET /api/v1/workforce/members/{memberId}/financial-statement` — unified read model, eventually aggregating posted ledger movements and clearly separated pending/custody/reimbursement sections.

These are proposed route shapes only. Do not implement them before request/response DTOs, permissions, compatibility adapters, error semantics, and client migration are reviewed.

## 6. Accounting and writer safety

- Item definitions and pending applications do not post journal entries.
- The future posting operation must use one canonical Workforce financial command and one database transaction for application state, ledger movement, and journal posting.
- Do not write to `workforce_ledger_entries` directly while `driver_ledger_entries` remains the active application writer and V56 mirror is active.
- Do not dual-write two ledgers.
- Do not bypass account validation, idempotency reservation, member locking, correction blockers, or append-only guards.
- A deduction's journal mapping and payable effect must be family-controlled and policy-approved; the UI cannot select an arbitrary debit/credit direction.
- Recoverable advances remain separate assets; payments settle payable; custody is not an automatic deduction.

## 7. Required test matrix before implementation approval

1. Tenant A cannot read/update/apply Tenant B's definition or application.
2. Duplicate item code within a tenant rejected; same code in two tenants allowed.
3. Inactive definition cannot be newly applied; old applications remain readable.
4. Definition rename/deactivation does not mutate historical application snapshots.
5. Earning and deduction families produce only their permitted balance direction.
6. A deduction cannot be submitted/approved/posted without required reason and authorization.
7. Separation of duties is enforced for configured high-risk approvals.
8. Fixed/hour/day/shift/quantity/manual calculations use server-approved facts and exact rounding.
9. Missing/ambiguous source facts reject safely; no fallback to client totals.
10. Duplicate request key with same fingerprint returns the same application; changed fingerprint conflicts.
11. Concurrent approval/post requests produce one posting only.
12. Application, ledger, and journal commit or roll back atomically.
13. Posted application update/delete is rejected; correction is append-only.
14. Existing agreement-local compensation components continue to work during catalog adoption.
15. Existing legacy Driver-ledger routes and V56 mirror behavior remain unchanged until a separately approved coordinated writer cutover.

## 8. Implementation gates

Catalog and agreement-link implementation is limited to the scope described above. No application lifecycle or posting implementation begins until:
- Product owner approves the item families and v1 scope.
- Full code/data inventory identifies all current movement writers/readers and tenant usage.
- Exact account mapping is reviewed with accounting domain rules.
- The canonical Workforce ledger writer transition is separately designed and approved.
- MySQL migration constraints and rollback are tested on disposable copies.

Current branch status: V60/V61 and catalog/agreement backend endpoints are present; their CI run for commit `7ffd3bf04a3c2318aefd74e1dc3ec800cacc112d` was still in progress at the time this document update was prepared. The application table, application APIs, approval workflow, ledger posting, and production rollout are not implemented. This branch work does not authorize production migration or a ledger-writer cutover.
