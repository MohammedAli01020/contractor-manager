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

### Current-code observation

The current driver model includes a `WorkerType` distinction (`DRIVER` / `WORKER`) and shift-specific morning/evening/night rates. This is a useful starting point but is not yet a complete general workforce model for a multi-activity ERP.

### Maturity

- **Agreed direction:** Contractor Manager must support people beyond drivers.
- **Proposed design:** a general workforce member profile with configurable classifications and separate system-user identity.
- **Still to decide:** whether the first implementation should generalize the current driver/worker model in place or introduce a separate person/workforce aggregate.

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
- Design HR phases and boundaries, including attendance, leave, payroll, deductions, settlements, and access controls.
- Define measurement units and precision/rounding rules for quantity and time.
- Map each proposed workflow to journal events, approvals, audit, and period-close behavior.
- Review Flutter navigation and forms for a simple mobile-first experience.
- Keep a clear distinction between documented, approved, implemented, and tested capabilities.

## 11. Change control

This document records product direction and proposed architecture. It does not authorize code changes, database migrations, PR merges, or changes to `main`.

Each detailed design decision must be reviewed and explicitly approved before implementation.
