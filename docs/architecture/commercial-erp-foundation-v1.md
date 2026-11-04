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