# Commercial Contractor ERP Foundation — v1

**Status:** Working design note — decisions are recorded with explicit maturity labels  
**Date:** 2026-11-04  
**Branch:** `docs/commercial-erp-architecture-v1`

## 1. Product direction

The strategic product direction is a multi-tenant, commercially sellable ERP for contractors, built on shared capabilities and configurable activity templates.

The first commercial focus remains equipment contracting and road/curbstone work. The architecture should avoid hard-coding the whole product to that activity, but the first release must remain focused and deliverable.

This direction does not authorize implementation or changes to `main`.

## 2. Design principles

1. Keep one shared ERP foundation rather than a separate codebase for each contractor activity.
2. Separate work measurement from pricing and compensation.
3. Preserve the current shift workflow as the first operational workflow; do not replace it prematurely.
4. Add future operational models incrementally after their complete operational and accounting lifecycle is designed.
5. Treat actual transactions and posted accounting records as authoritative; estimates must remain distinguishable from actuals.
6. Keep company-specific configuration tenant-scoped.
7. Mobile-first Arabic/RTL remains a key product requirement.

## 3. Work measurement and commercial pricing

The design must distinguish these independent concepts:

- **Work measurement:** shift, elapsed hours, quantity with a unit of measure, or fixed-scope job.
- **Customer pricing:** per shift, per hour, per unit, or fixed contract/job amount.
- **Worker compensation:** per shift, per hour, per quantity, fixed amount, or another explicitly configured agreement where applicable.
- **Equipment cost:** estimated operating rate, measured usage cost, or actual recorded expenses.

These dimensions must not be assumed to match. For example, equipment may be recorded as one shift while the customer is billed by the installed meter of curbstone.

### Maturity

- **Agreed direction:** keep measurement, customer pricing, worker compensation, and equipment cost conceptually separate.
- **Current baseline:** shift-based operation is the existing first workflow.
- **Not yet approved for implementation:** hourly and quantity-based operational workflows, fixed-job workflows, and their detailed validation/accounting rules.

## 4. Operational record direction

A future common operational-record concept may unify reporting and links to projects, sites, equipment, and people while preserving specialized workflows such as shifts.

Do not assume that the existing `Shift` entity must immediately be replaced by a generic `WorkRecord` table. First compare:
- extending the current shift model safely;
- introducing a common parent/header with specialized detail records; or
- keeping separate operational records connected through shared interfaces and reporting.

The choice requires a data-model and migration impact review before implementation.

## 5. People and workforce model

The product must not be limited to drivers.

### Proposed general concept

Use a tenant-scoped **Person / Workforce Member** concept for people who participate in company operations. A person may have one or more work classifications or assignments, such as:

- driver;
- equipment operator;
- general/site worker;
- technician or mechanic;
- site supervisor or foreman;
- engineer or technical staff;
- administrative or finance staff.

These are examples, not a closed or mandatory list. Companies should be able to configure classifications appropriate to their activity.

### Important separations

- A workforce member is not automatically a system login.
- System users, authentication, permissions, and approval authority remain separate concepts.
- A person may hold multiple operational assignments where the business requires it.
- Work classification must not by itself grant application permissions.
- Compensation basis and rates should be configurable separately from the person's classification.
- The actual agreed compensation/rate used for a completed operational transaction must be snapshotted on that transaction so later rate changes do not rewrite history.

### Current-code observations — `feature/financial-foundation`

The following observations were verified from the backend and Flutter source on the reviewed branch:

- The current JPA entity is `Driver`, stored in the `drivers` table and tenant-scoped through `BaseTenantEntity`.
- `WorkerType` currently has only `DRIVER` and `WORKER`.
- The profile stores name, phone, national ID, availability, active state, notes, and three shift rates (morning/evening/night). It does not yet represent general job titles, departments, employment contracts, or HR lifecycle.
- The API request requires all three shift rates, and the mobile edit dialog exposes only driver/worker classification and those shift rates.
- `Shift` currently has a required `driverId` and stores a driver wage snapshot. The shift workflow therefore depends directly on the existing driver identity.
- On shift completion, `ShiftService` posts the wage expense/payable journal, writes an `EARNING` entry to the driver ledger, and can recover a recoverable advance. This is an existing financial workflow that must be preserved during any workforce evolution.
- `driver_ledger_entries` is keyed by `driver_id` and references the existing `drivers` table. Its transaction types currently include earning, payment, advance, and adjustment (with later migrations adding related advance/reversal behavior).
- The mobile screen and labels still describe the directory as drivers and workers, rather than a general employee/workforce directory.

### Preliminary architectural recommendation — not yet approved for implementation

Do not simply rename `Driver` to `Employee`, and do not immediately replace the `drivers` table. Existing shifts, financial ledger entries, migrations, APIs, and Flutter screens depend on it.

Before choosing a migration, compare at least these paths:

1. **Incremental generalization:** evolve the current driver/worker profile and add general workforce capabilities while preserving existing identifiers and shift/ledger relationships.
2. **Separate person master:** introduce a tenant-scoped person/employee master, link the existing driver operational profile to it, and migrate references gradually.
3. **Full replacement:** migrate shifts and ledger foreign keys to a new workforce identity only if the benefits justify the larger migration and compatibility risk.

The preferred option should minimize disruption to completed shifts and posted financial history, preserve tenant isolation, and allow one person to have multiple classifications/assignments without granting system permissions automatically.

### Maturity

- **Agreed direction:** Contractor Manager must support people beyond drivers.
- **Agreed product scope:** HR capabilities belong in the product roadmap.
- **Proposed design:** a general workforce member profile with configurable classifications and separate system-user identity.
- **Still to decide:** the aggregate/table strategy, whether one person can have multiple concurrent employment relationships, and the migration path from `drivers`.

## 6. HR scope

**Agreed product requirement:** the product is intended to support Human Resources capabilities, not only operational worker records.

The HR domain is expected to cover, subject to detailed design and phased delivery:

- employee/workforce profiles and employment status;
- job titles, departments, classifications, and assignments;
- employment terms and compensation agreements;
- attendance, check-in/check-out, and attendance corrections;
- leave types, balances, requests, and approvals;
- deductions, penalties, and other adjustments, with clear reasons and audit history;
- payroll calculations, payslips, and payroll-period processing;
- employee settlements and payment tracking;
- employee documents and relevant history, with controlled access.

### Domain boundaries and safeguards

- HR records, attendance, payroll calculations, and actual cash/bank payments are related but distinct processes.
- Payroll calculation must not itself be treated as proof that money was paid.
- Payroll posting/payment must integrate with the accounting and treasury foundations without double-counting wages or expenses.
- Advances, loans, custody, penalties, and salary deductions must have explicit classifications and workflows; they must not be silently merged into generic expenses.
- Changes to attendance, approved payroll, and closed payroll periods require authorization, traceability, and correction rules.
- Sensitive employee and payroll information must be protected by granular role-based access and tenant isolation.
- Not every workforce member must be a system user; application login and permissions remain separate from employment records.

### Delivery maturity

- **Agreed product scope:** HR capabilities belong in the product roadmap.
- **Not yet specified:** detailed HR schema, labor-law/local compliance requirements, payroll formulas, tax/social-insurance treatment, overtime rules, attendance devices/geofencing, and approval matrices.
- **Delivery recommendation:** establish the workforce/person foundation early, then deliver HR modules in phases. Do not delay the first contractor-focused release by attempting a complete HR suite at once.

## 7. Customer, project, contract, and site separation

The commercial model should distinguish:

- **Customer:** the party buying work or services.
- **Project / Contract:** the commercial engagement, scope, rates, and financial follow-up.
- **Site:** the physical location where work is performed.
- **Operational record:** the actual shift, hours, quantity, or job performed.
- **Progress claim / invoice:** the commercial valuation or billing of completed work.

A project may include multiple sites; a site may be used by more than one project where appropriate. The exact relationship and billing lifecycle require a separate detailed design.

## 8. Financial integrity

1. A single real-world expense must remain one financial transaction even if linked to multiple operational references.
2. Driver/worker advances must remain distinct from expenses and wages.
3. Estimates must not be posted as actual expenses automatically.
4. Customer billing, worker compensation, equipment costs, and actual expenses must not be double-counted.
5. Accounting entries must be generated from validated business events and follow the existing double-entry foundation.
6. Corrections to completed/posted operations must preserve audit history and follow approval/period controls.

## 9. Configuration and activity templates

The shared foundation should eventually support tenant-level settings and activity templates, for example equipment contracting, road/curbstone works, and finishing/construction works.

Templates may provide suggested terminology, units, classifications, and workflows. They must not create separate incompatible data models or silently impose accounting behavior.

The exact template mechanism and subscription packaging are future design work.

## 10. Review work required before implementation

- Map current shift, equipment, driver/worker, expense, journal, site, and inventory structures.
- Identify what can remain unchanged and what requires extension.
- Compare operational-record data-model options and migration risk.
- Design customer, project/contract, site, and progress-claim relationships.
- Define workforce profile, classifications, assignments, compensation rules, and separation from system users.
- Decide the workforce aggregate/table strategy and safe migration path from the existing driver/worker model.
- Design HR phases and boundaries, including attendance, leave, payroll, deductions, settlements, and access controls.
- Define measurement units and precision/rounding rules for quantity and time.
- Map each proposed workflow to journal events, approvals, audit, and period-close behavior.
- Review Flutter navigation and forms for a simple mobile-first experience.
- Keep a clear distinction between documented, approved, implemented, and tested capabilities.

## 11. Change control

This document records product direction and proposed architecture. It does not authorize code changes, database migrations, PR merges, or changes to `main`.

Each detailed design decision must be reviewed and explicitly approved before implementation.

### Additional verified findings — financial and identity relationships

A second read-only pass inspected the shift service, worker ledger, ledger API/DTOs, user entity, and migrations:

- `ShiftService.finish` locks the referenced driver before wage posting, creates the wage journal entry, stores its ID on the shift, and appends an `EARNING` ledger row. It also checks the recoverable-advance balance and may post a separate advance-recovery journal and ledger row in the same transaction.
- The shift wage is therefore a recognized entitlement, not proof of cash payment. The payment endpoint is separate and reduces the worker's ledger balance while posting a cash/bank journal entry.
- Advance handling already distinguishes two cases: an advance against earned payable (reduces the available worker balance immediately) and a recoverable advance (tracked as an asset and recovered from later earnings). Both remain separate from generic expenses.
- Worker ledger rows are append-only at the database boundary. Corrections use compensating/reversal movements; they must not update or delete posted history.
- Ledger rows carry tenant, driver, journal, creator, optional shift, idempotency key, amount, and balance effect. Database foreign keys enforce tenant-consistent links to drivers, shifts, and journal entries. The database also enforces one original earning per shift.
- The ledger statement, payment, and advance APIs are currently nested under `/drivers/{driverId}/ledger`; the repository aggregates balances by `driverId`. This is a wider migration surface than changing a screen label or Java class name.
- The current `User` entity represents a tenant login account with username, password, role, active state, and login metadata. It is not a workforce/employee record and should remain a separate security identity.
- The shift itself stores the driver wage snapshot and the journal reference. A completed/posted shift is protected from changing its financially significant source fields; audited correction flows add reversal/replacement records rather than rewriting the original wage.

### Consequences for the workforce and HR architecture

1. A generic workforce identity must become the stable person reference for new HR and financial capabilities; the existing driver ID must remain valid for historical shifts and ledger rows during transition.
2. Avoid a direct rename/drop of `drivers`, `driver_id`, or `driver_ledger_entries`. Existing foreign keys, unique constraints, idempotency, correction logic, and tenant-scoped balance queries make that unsafe.
3. The migration should be additive first: introduce the general workforce identity, link each existing driver profile to it, and backfill existing records without changing historical shift or journal IDs.
4. New worker types should be able to participate in suitable operational records and receive entitlements without pretending that every person is a driver. The shift workflow can initially continue to require a driver/operator profile if that is the business rule for equipment shifts.
5. Before creating a generic worker ledger, compare whether to (a) generalize the current ledger behind a workforce-member abstraction while preserving the physical table during a compatibility period, or (b) create a new workforce ledger and migrate/read through old entries. Either path must preserve immutable history, idempotency, journal links, balance semantics, and tenant isolation.
6. Payroll must not create a second wage expense for an amount already recognized through a completed shift. The design needs explicit earning sources and payroll inclusion rules so shift earnings, fixed/hourly salary, deductions, advances, and payments reconcile without duplication.
7. Attendance is not automatically a payable event. Attendance records may feed payroll calculation, but only approved compensation rules and a defined posting event should create accounting entries.

### Review status

- **Verified in current code:** driver-specific shift and ledger identity; separate login users; separate wage recognition and cash payment; two advance behaviors; append-only ledger and audited correction approach.
- **Recommended direction, pending explicit approval:** additive workforce identity linked to current driver profiles, with backward-compatible references during migration.
- **Not decided:** physical design of a future generalized worker ledger; whether payroll consolidates shift earnings or consumes them as pre-calculated earning items; which non-driver roles may be assigned to each operational workflow.
- **No implementation performed:** this review changed documentation only; no application code, database migration, PR, or `main` branch was modified.
## 12. Workforce subledger and payroll architecture — design recommendation

### 12.1 Decision recommendation

**Recommended direction, pending explicit product approval:** one logical worker-payables subledger for all workforce members, not a separate ledger per worker type and not a second parallel payroll ledger. Preserve the current `driver_ledger_entries` implementation as the compatibility baseline while introducing a stable general workforce identity and migrating additively.

This is a logical/domain decision, not an instruction to rename or replace the current table immediately. The physical schema and migration sequence must be designed separately after reviewing every foreign key, journal dimension, API consumer, trigger, and integration test.

Why this direction:
- The current ledger already provides tenant-scoped idempotency, journal linkage, append-only protection, balance effects, and serialized balance-changing operations.
- Two independently authoritative ledgers would make the same worker's payable balance difficult to reconcile and could allow double payment or inconsistent statements.
- A direct rename of `driver_id` is unsafe: shifts, ledger rows, journal lines, repositories, API routes, unique constraints, and database triggers still depend on driver identity.
- The existing worker payable account code 2100 and wage expense code 5000 are named and described for drivers. Their accounting meaning should be generalized to workforce payables and labor/compensation expense through a deliberate chart-account compatibility plan, not by silently changing deployed account codes.

### 12.2 Target conceptual model

Use these distinct concepts:

- **WorkforceMember:** the tenant-scoped human identity and core profile. It is not a login account.
- **WorkforceAssignment:** one or more roles/classifications a person performs (driver, operator, technician, supervisor, engineer, administrative staff, etc.), with effective dates and optional department/site/project context.
- **OperationalProfile:** specialized capability data needed by a workflow. The existing Driver profile remains the first such profile and keeps its operational availability and legacy rates during transition.
- **CompensationAgreement:** effective-dated rules for shift, hourly, quantity, fixed salary, or other approved compensation. A worker classification must not hard-code the pay basis.
- **WorkerPayableSubledger:** one chronological, append-only statement per workforce member, with entries sourced from approved business events.
- **PayrollPeriod / PayrollRun:** a period-scoped calculation, review, approval, and reconciliation process. It is not a second balance ledger.
- **SystemUser:** a separate authentication and authorization identity. Linking a workforce member to a system user is optional and must not grant permissions automatically.

### 12.3 How earnings and payroll must work

Do not make every attendance or payroll calculation create a new wage expense. Separate the lifecycle into:

1. **Measurement/input:** completed shifts, approved attendance, approved hours, accepted quantities, fixed salary terms, overtime, and approved adjustments.
2. **Calculation:** calculate the amount due under the effective compensation agreement and show the source details. A calculation or draft payroll run is not yet an accounting posting.
3. **Recognition:** create one earning/payable event for each approved compensation source, with a unique source identity and idempotency key. The existing completed-shift flow already recognizes shift earnings; payroll must consume and reconcile those earnings rather than post them again.
4. **Settlement:** record actual cash/bank payment separately, reducing the payable through the existing payment semantics. A payslip or approved payroll is not proof of payment.

Recommended source rules:
- **Shift-paid worker:** shift completion continues to recognize the shift earning once. Payroll period reporting includes those existing earning entries as items already recognized; it does not debit wage expense or credit payable a second time.
- **Fixed monthly or periodic salary:** approved payroll posts the salary expense and workforce payable once for the approved period. Attendance, leave, overtime, and deductions are calculation inputs and audit evidence.
- **Hourly or quantity-paid worker:** approved time/quantity records feed payroll calculation; the approved payroll run recognizes the resulting payable once, unless a future workflow explicitly recognizes each approved work item earlier.
- **Mixed compensation:** each component has a distinct source and rule. The same source cannot be recognized both at shift completion and again in payroll.
- **Corrections after posting:** use reversal/adjustment entries and a re-run/reconciliation process; never edit posted ledger or journal rows.

### 12.4 Advances, deductions, and settlements

- Keep advances as explicit worker financial events, not generic expenses.
- Preserve the distinction between an advance against already-earned payable and a recoverable advance/employee receivable.
- Recoverable advances are reduced only through explicit recovery entries linked to an earning/payroll source; the recovery must not create a second wage expense.
- Penalties, deductions, loans, custody, reimbursements, and end-of-service settlements need distinct business types, authorization, reason codes, effective dates, and accounting mappings. Do not represent all of them as an untyped negative salary number.
- Actual payment remains a cash/bank event and must pass the centralized posting and cash/bank balance controls.

### 12.5 Accounting dimensions and compatibility work

The current accounting journal line has a `driver_id` dimension with a foreign key to `drivers`; the current payable and wage expense system accounts are also driver-named. A generalized workforce ledger therefore requires a coordinated design for:

- a tenant-safe workforce-member dimension on journal lines, with a compatibility/read strategy for existing `driver_id` history;
- worker payable account semantics and account-code/name compatibility across existing tenants;
- mapping every legacy driver to exactly one workforce member before new generic entries are accepted;
- preservation of old shift, ledger, journal, and payment IDs and source links;
- uniqueness and idempotency across old and new API routes so retries cannot create duplicate movements;
- report behavior that combines legacy and new entries into one continuous statement without double-counting;
- row-lock ordering and database constraints for concurrent earnings, recoveries, payments, and payroll posting.

Do not drop `driver_id` from journal lines or `driver_ledger_entries`, or remove account code 2100/5000, as part of the first workforce migration. A later cleanup is a separate approved migration after all consumers and historical reports are proven compatible.

### 12.6 Recommended delivery sequence

1. Finalize and approve the target workforce identity, assignment, and compensation concepts.
2. Inventory all foreign keys, triggers, repositories, API/mobile consumers, journal dimensions, and tests that depend on `drivers` and `driver_id`.
3. Add the workforce identity and a one-to-one legacy driver link; backfill existing drivers without changing their IDs or historical transactions. Validate tenant isolation and duplicate/missing mappings.
4. Add workforce-aware read models and statements that can display legacy driver entries under the new workforce identity. Keep existing APIs working.
5. Route new generic workforce earning/payment/advance events through one subledger service and one idempotency/locking policy. Do not dual-write to two independently authoritative ledgers.
6. Add compensation agreements and payroll calculation in draft mode first; prove that shift earnings are included but not re-posted.
7. Add approved payroll recognition for non-shift compensation, followed by payroll payment/settlement integration and period-close controls.
8. Only after reconciliation and compatibility evidence, consider deprecating legacy driver-specific APIs or columns in a separate release.

### 12.7 Required acceptance tests before implementation is considered safe

- Every existing driver maps to exactly one workforce member; no historical shift, ledger entry, or journal line loses its identity.
- Tenant A cannot read, mutate, pay, or reconcile a workforce member or ledger entry belonging to tenant B.
- A completed shift appears once in the worker statement and once in the payroll-period reconciliation, but creates only one wage expense/payable recognition.
- Fixed salary and hourly/quantity payroll are recognized once on approval; draft, recalculation, retry, and duplicate approval do not create extra journals.
- Partial payments, recoverable advances, advance recovery, deductions, reversals, and settlements reconcile to the same worker payable balance.
- Concurrent payroll approval, shift completion, advance recovery, and payment cannot overstate available payable or create duplicate source recognition.
- Existing mobile and API flows continue to work throughout the compatibility phase.
- MySQL integration tests verify foreign keys, triggers, idempotency, rollback atomicity, lock ordering, and tenant isolation; unit tests alone are insufficient.

### 12.8 Status

- **Recommended architecture:** one logical workforce payable subledger, one general workforce identity, optional operational profiles, and payroll as a calculation/approval/reconciliation process.
- **Explicitly not recommended:** a separate independent payroll balance ledger; direct table rename/drop; posting shift wages again during payroll; treating attendance as automatic expense recognition.
- **Approved by product owner:** adopt the target model and additive migration direction as the project architecture baseline. This approval authorizes detailed technical planning, not automatic schema/code changes or PR merges.
- **No implementation performed:** this section records the approved design only; application code, database migrations, PRs, and `main` remain unchanged.
### 12.9 Approved product-owner decisions (2026-11-04)

The product owner approved the following architecture baseline:

1. One general workforce identity per person, independent of job classification and system login.
2. A person may hold multiple effective-dated roles/assignments and operational profiles.
3. One logical append-only worker-payables subledger per person, with typed and traceable source movements; no parallel authoritative payroll balance ledger.
4. Compensation must support shift, hourly, daily, quantity/piecework, fixed salary, and mixed components through effective-dated agreements.
5. Payroll calculates, reviews, approves, and reconciles compensation. Existing shift earnings are included as already recognized and must never be posted twice.
6. Advances remain separate from generic expenses; earned-payable advances and recoverable advances retain distinct accounting behavior.
7. Actual payment is separate from earning recognition and is posted through cash/bank controls.
8. Existing driver, shift, journal, and ledger history must be preserved through an additive, backward-compatible migration. No destructive rename/drop in the first migration.
9. HR delivery is phased; workforce core and compatibility come before full attendance, leave, payroll, and settlement capabilities.
10. This approval authorizes the next detailed technical design and migration plan only. Implementation, schema changes, and PR merges remain subject to review and the user's explicit authorization.

## 14. Workforce design artifacts and current review status

The following detailed artifacts form the current Workforce Core design package:

- `workforce-legacy-dependency-inventory-v1.md` — current driver/shift/ledger/journal/API/mobile dependency inventory.
- `workforce-core-migration-proposal-v1.md` — additive identity, assignment, subledger and accounting-dimension migration proposal.
- `workforce-compensation-and-hr-design-v1.md` — compensation agreement components, legacy three-rate mapping, payroll stages, HR boundaries and acceptance scenarios.

### 14.1 Compensation decisions recommended

- Convert each legacy driver's morning/evening/night rates into three SHIFT agreement components effective at the agreed cutover date; do not backdate or rewrite historic shifts.
- Keep legacy rate columns as a temporary compatibility fallback, monitored and removed only after all tenants reconcile.
- Model MIXED compensation as multiple typed components, not an opaque formula.
- Make roles/assignments multi-role and effective-dated; operational eligibility remains separate.
- Keep shift earning recognition in the existing shift completion transaction. Payroll reports those earnings as already recognized and never posts them a second time.
- Draft payroll and attendance have no accounting effect. Approved non-shift earnings post once; actual payment remains a separate cash/bank settlement.
- Keep one authoritative worker payable subledger.

### 14.2 Current status and authorization boundary

- Architecture and migration direction: approved by product owner.
- Legacy dependency inventory: documented.
- Detailed compensation/HR design: documented as recommended design for review.
- Code and database implementation: not started.
- The financial source branch reviewed remains at `e03e49b1fbe882e08dde702acb7a9ff3fcd05c84` at this checkpoint. Revalidate branch head and rerun relevant inventory before implementation.
- No PR merged and no change made to `main`.
