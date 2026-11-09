# WF12C — Workforce Ledger and Journal Dimension Design Proposal

**Status:** Proposal for review — not an implementation authorization  
**Working branch:** `feature/wf12c-shift-workforce-identity`  
**Scope:** Workforce-native worker balances, earnings, advances, payments, recoveries, corrections, journal dimensions, historical migration, and eventual legacy retirement.

## 1. Objective and non-goals

Make `workforce_member_id` the only operational identity for worker financial activity. Replace the driver-keyed worker ledger with one Workforce-native ledger and remove the old Driver financial path after a reconciled cutover.

This proposal does **not** authorize:
- a Flyway migration or schema change;
- dual-writing two authoritative ledgers;
- data backfill or production cutover;
- changing or deleting posted journal entries/lines;
- deleting `drivers`, `driver_ledger_entries`, `shifts.driver_id`, or Driver APIs;
- changing `main` or merging a pull request.

The exact DDL and endpoint contracts remain subject to review after the complete repository and tenant-data inventories.

## 2. Evidence available

### 2.1 WF06 tenant 8 sample

The isolated database `contractor_manager_wf06_test` (MySQL 8.0.44) was inspected read-only.

- 17 legacy ledger rows were present: 15 `EARNING` and 2 `CORRECTION_REVERSAL`.
- Their net payable movement was 66,460; gross earnings were 67,435 and reversals were 975.
- No `PAYMENT`, `ADVANCE`, `RECOVERABLE_ADVANCE`, `ADVANCE_RECOVERY`, or `ADJUSTMENT` rows appeared in this tenant's grouped result. This is not evidence that other tenants have none.
- All 17 rows joined to a Driver and a Workforce member. Driver ID 4 mapped to Workforce member ID 7, proving that IDs cannot be copied by numeric equality.
- The six journal entries examined (1, 2, 3, 15, 16, 17) each had two lines and balanced to zero.
- The two tested correction chains correctly linked original earning, reversal, replacement, and their journal entries.
- The six inspected journals retained `journal_lines.driver_id`; no Workforce dimension exists on those historical lines.
- Shifts 1 and 13 stored both Driver and Workforce IDs, with the IDs matching the linked person in these two examples.

These results validate the inspected sample only. They do not reconcile every journal, every tenant, or all production history.

### 2.2 Existing implementation constraints

The current legacy ledger is not disposable data. It is the active financial source for earnings, payments, advances, recoveries, statements, and corrections. It has tenant-scoped uniqueness/idempotency, journal/shift links, append-only protections, and correction-chain references.

The current shift correction flow reverses the old earning and posts a replacement; it blocks correction after certain downstream payment/advance/recovery consumption. The Workforce replacement must preserve this safety unless a separately designed settlement workflow is approved.

## 3. Target model

### 3.1 Canonical identity

- `workforce_members` is the only canonical person identity.
- Every new worker financial movement references `(tenant_id, workforce_member_id)`.
- Role, assignment, compensation agreement, work-period type, and actual work time remain separate concepts.
- Driver/operator is an operational capability/assignment, not a separate person identity.

### 3.2 Proposed ledger entity

Proposed table name: `workforce_ledger_entries`.

The final DDL should include at least:

| Field | Purpose |
|---|---|
| `id` | Immutable ledger movement identity |
| `tenant_id` | Tenant boundary |
| `workforce_member_id` | Canonical worker identity |
| `transaction_type` | Canonical movement type |
| `transaction_date` | Effective transaction date |
| `amount` | Positive absolute movement amount |
| `balance_effect` | Signed effect on payable balance |
| `description` | Immutable explanation |
| `operation_scope` | Idempotency operation namespace |
| `client_request_key` | Stable caller retry key |
| `request_fingerprint` | Request identity/integrity evidence, aligned with current reservation semantics |
| `source_type`, `source_id` | Domain event/source provenance |
| `shift_id`, `correction_id` | Optional source references where applicable |
| `journal_entry_id` | Posted journal link, tenant-safe |
| `created_by_user_id`, `created_at` | Audit metadata |
| `legacy_source_entry_id` | Original legacy ledger row ID during migration; plain provenance value, not an FK to the retiring table |
| `legacy_driver_id` | Optional historical identity provenance only; never used for operational lookup or authorization |

The precise treatment of `request_fingerprint` must match the existing `financial_idempotency_keys` lifecycle. Avoid storing duplicate fingerprint state in two places unless the design defines one authoritative owner.

### 3.3 Database invariants

The final schema and tests must preserve or strengthen:

1. Tenant-safe composite FK from ledger row to Workforce member.
2. Tenant-safe journal, shift, and correction references.
3. Positive `amount`; signed `balance_effect` with explicitly allowed zero-effect movement types.
4. Append-only ledger rows; corrections create compensating movements.
5. Unique idempotency reservation by tenant, operation scope, and request key.
6. One original earning per shift.
7. Explicit uniqueness for each correction reversal and replacement event.
8. Unique journal association where the business rule requires one ledger movement per journal.
9. Stable Workforce-member row locking for earnings, payments, advances, and recoveries.
10. No cross-tenant reference can pass through application validation alone; enforce it in the database.

Do not rely on a nullable unique key alone to enforce one original earning per shift in MySQL. Use an explicit generated key or another database-enforced design whose behavior is integration-tested on MySQL.

## 4. Transaction taxonomy and balance semantics

Preserve the current distinctions; do not collapse all advances into expenses or payable balance.

| Movement | Proposed canonical meaning | Payable `balance_effect` |
|---|---|---:|
| `EARNING` | Earned wage/compensation | Positive amount |
| `PAYMENT` | Cash/bank paid to worker | Negative amount |
| `ADVANCE` | Advance against already-earned payable, where current API semantics apply | Negative amount |
| `RECOVERABLE_ADVANCE` | Recoverable amount owed back to contractor | Zero |
| `ADVANCE_RECOVERY` | Portion of recoverable advance recovered from later earning | Negative amount |
| `CORRECTION_REVERSAL` | Reverse a prior earning | Negative old amount |
| `ADJUSTMENT` | Explicit approved adjustment only after historical usage and authorization are understood | Signed, policy-controlled |

Recoverable advance outstanding remains a separate calculation:
- increase by `RECOVERABLE_ADVANCE.amount`;
- decrease by `ADVANCE_RECOVERY.amount`;
- never mix this asset balance into worker payable.

The current tenant 8 sample contained no adjustment or advance rows. Before finalizing the taxonomy, inventory every tenant and inspect the service/API meaning of each actual row. Do not infer that absence in WF06 means a type can be dropped.

## 5. Correction chain

A correction must preserve the original financial history:

1. Original earning remains immutable.
2. Reversal is a new `CORRECTION_REVERSAL` movement linked to the correction and original earning/journal.
3. Replacement earning is a new `EARNING` movement linked to the same correction and replacement journal.
4. The correction record retains explicit original/reversal/replacement references.
5. Journal reversal uses the existing `reversal_of_entry_id` relation.
6. Every journal remains balanced and posted journals remain immutable.

The tested sample confirms this chain for two corrections. The replacement design must also preserve existing blockers when a later payment, earned-wage advance, or recovery has consumed the payable. Do not weaken those blockers as part of migration.

## 6. Journal dimension proposal

### 6.1 Historical rows

- Keep historical `journal_lines.driver_id` unchanged.
- Do not rewrite posted journal lines to replace Driver IDs with Workforce IDs.
- Backfill a Workforce dimension only where a tenant-safe, one-to-one mapping is proven.
- Preserve the original Driver dimension as historical provenance for as long as the accounting retention policy requires.

### 6.2 New postings during transition

Add nullable `journal_lines.workforce_member_id` with a tenant-safe FK to `workforce_members`.

For new worker-related postings:
- `workforce_member_id` is the canonical dimension;
- `driver_id` may be populated as a compatibility dimension only while a legacy consumer demonstrably requires it;
- the two IDs must be validated as belonging to the same tenant and same person;
- new application logic must not resolve the person by Driver ID;
- the compatibility population must have a documented removal milestone.

This is not dual-writing two ledgers. It is temporary dual-dimension tagging on one immutable journal line while old readers are retired.

After all consumers migrate, new postings stop populating `driver_id`; historical values remain untouched. Dropping the column is a separate final retirement decision after all retained reporting and audit requirements are addressed.

### 6.3 Account mapping

Do not rename or replace existing account codes 2100 (worker payable) and 5000 (worker wage expense) based only on their current labels. Review account semantics, chart configuration, reports, and postings across all tenants before any account migration.

## 7. API and client transition

Target API surface should be Workforce-member keyed, for example:

- `GET /api/v1/workforce-members/{workforceMemberId}/ledger`
- `POST /api/v1/workforce-members/{workforceMemberId}/ledger/advances`
- `POST /api/v1/workforce-members/{workforceMemberId}/ledger/payments`

These are proposed route shapes, not approved final contracts. Reuse existing authorization, tenant scoping, idempotency, cash/bank validation, and response semantics.

Migrate together:
- shift create/update/start/finish/correction DTOs and services;
- live candidate API;
- Flutter workforce directory and shift editor;
- ledger statements, payment/advance flows, reports, exports, and web/admin surfaces.

Keep old routes as thin compatibility adapters only for a defined migration window. They must delegate to the same Workforce service and must not maintain a separate ledger. Remove them after client usage evidence and deprecation criteria pass.

## 8. Historical migration and cutover

No permanent dual-write of old and new ledgers.

Recommended controlled sequence:

1. Complete repository-wide consumer inventory and all-tenant data inventory.
2. Approve exact schema, API, idempotency, locking, and correction design.
3. Implement Workforce-native workflows and tests in an isolated branch/database.
4. Prepare deterministic mapping from every legacy Driver to exactly one Workforce member; report missing, duplicate, cross-tenant, and ambiguous mappings.
5. Build a repeatable, dry-runnable migration that copies every legacy movement, preserving amount, date, type, signed effect, description, actor/time, journal/shift/correction links, idempotency provenance, and legacy source row ID.
6. Reconcile per-worker/per-type movement counts and totals, payable balances, recoverable-advance balances, journal links, and control-account totals.
7. Cut over writes at a controlled maintenance boundary: stop old writes, take a verified backup, migrate final delta, reconcile, then enable Workforce writes. Do not allow concurrent writes to both ledgers.
8. Run read-only comparison and application smoke/regression tests before reopening normal traffic.
9. Keep the old tables read-only during an agreed observation/rollback window. If rollback is needed, use a documented restore/forward-recovery procedure; never delete the old data during the observation window.
10. Once exit criteria pass, remove old API adapters and runtime references, then delete legacy schema in a separate approved Flyway migration.

The exact downtime/maintenance strategy and rollback mechanics must be proven against realistic database size before production scheduling.

## 9. Legacy deletion is a required completion milestone

The project is not considered Workforce migration-complete while any of these remain active sources of truth or operational dependencies:
- `driver_ledger_entries`;
- `drivers` as a person identity or financial lock owner;
- `shifts.driver_id` as a required operational identity;
- Driver-keyed ledger APIs and client payloads;
- `journal_lines.driver_id` as a required dimension for new postings.

Deletion is allowed only when the retirement plan's exit criteria are evidenced: full migration/reconciliation, no unresolved mappings, no runtime/client/report/export/integration consumers, successful tests, verified restorable backup, and separately reviewed deletion migration. Historical journal values may remain as immutable audit data; they are not to be rewritten merely to make the schema look uniform.

## 10. Open decisions before DDL approval

1. Full tenant inventory: are there `ADJUSTMENT`, payment, advance, recovery, or orphan legacy rows outside WF06 tenant 8?
2. Exact idempotency ownership: keep `financial_idempotency_keys` as the sole reservation authority or consolidate reservation metadata into the ledger under a formally equivalent model?
3. Correction settlement: what approved workflow, if any, handles a correction after downstream payment/recovery consumption? Current blockers remain until a separate design is accepted.
4. Journal compatibility: which reports, exports, integrations, and old application versions still require `driver_id`?
5. Historical retention: should `legacy_driver_id` and `legacy_source_entry_id` remain as non-FK provenance columns, or should a permanent migration map preserve this provenance separately?
6. Production cutover: what maintenance window and restore/forward-recovery procedure are feasible for actual data volume?

## 11. Approval boundary and next implementation task

This proposal is for review. The next task is to finish the full repository and all-tenant data inventories, answer the open decisions, then present exact DDL/API contracts for approval.

Until explicit approval of that implementation plan:
- no schema migration;
- no dual-write;
- no backfill;
- no API cutover;
- no deletion;
- no production action;
- no main-branch change or PR merge.
