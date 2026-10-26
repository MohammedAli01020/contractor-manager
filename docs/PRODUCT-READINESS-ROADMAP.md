# Contractor Manager — Product Readiness Plan

**Branch:** `feature/platform-owner-activation`  
**Status:** Product requirements and delivery checklist; implementation status must be verified separately.  
**Purpose:** Evaluate Contractor Manager as a commercial product for construction companies, not merely as an application that runs and supports login.

## 1. Product principle

A successful release means a real contracting company can onboard safely, configure its operation, use the system for daily work, trust its financial records, recover from failures, and obtain support. A successful build or login alone is not product acceptance.

For every capability, distinguish:
- **Planned:** documented but not implemented.
- **Implemented:** code exists and has been reviewed.
- **Tested:** relevant automated/manual checks were actually run and results recorded.
- **Accepted for release:** end-to-end workflow, security, usability, operations, and recovery expectations have been met.

Do not describe a feature as complete based only on documentation, source files, a successful build, or one happy-path test.

## 2. Product areas that must work together

### A. Tenant lifecycle and onboarding
- Platform Owner securely creates and manages a company (tenant) and its first company administrator.
- The company administrator can securely activate the account, sign in, and understand the first steps.
- Each company sees only its own data, including in APIs, exports, logs, background tasks, and reports.
- Company activation, suspension, subscription state, and recovery paths have explicit behavior.
- The first-use journey explains how to add drivers, equipment, sites, and start recording shifts.
- Avoid public self-registration unless it is explicitly enabled under a controlled policy.

### B. Daily operational workflow
- Company can manage drivers, equipment, sites, and any required master data.
- Users can create, review, start, complete, cancel, or correct shifts according to documented rules.
- Availability conflicts and concurrent requests are handled safely; a driver or equipment item cannot accidentally be assigned to conflicting active shifts.
- Historical shift values preserve the rates and costs that applied when the shift was recorded.
- Validation errors are understandable, and network retries do not create duplicate operations.
- Inactive or unavailable records show clear reasons and safe next actions.

### C. Financial trust and reconciliation
- Driver statements include opening balance, wages, advances, approved deductions, bonuses/allowances, actual payments, adjustments, and closing balance.
- Equipment operating result, company cash movement, supplier liabilities, and owner/partner settlements remain distinct concepts.
- Wages and expenses are not double-counted; estimated operating costs are not silently treated as actual expenses.
- Purchase, inventory issue, return, payment, correction, and monthly-close flows reconcile and are idempotent.
- Posted financial movements are corrected through audited reversals/corrections, not silent deletion.
- Reports and exports reconcile exactly to approved ledger and closing balances.
- Do not launch financial claims or reports until representative scenarios and edge cases reconcile.

### D. Roles, permissions, and security
- Define least-privilege permissions for Platform Owner, company ADMIN, and any future employee/driver roles.
- Enforce authorization in the backend, not only by hiding UI controls.
- Test cross-tenant access, privilege escalation, disabled accounts, expired activation/recovery links, replay, and revoked sessions.
- Secure initial Platform Owner bootstrap, email verification/recovery, and mandatory owner TOTP as specified by the security roadmap.
- Keep secrets, passwords, tokens, TOTP secrets, recovery codes, and unnecessary personal/financial information out of logs.
- Keep monitoring and administration surfaces private or strongly authenticated.

### E. Commercial subscription and access lifecycle
- Define the launch plan, price, usage limits, billing-period calculation, grace period, renewal, and delayed-payment-review policy before billing implementation.
- Enforce subscription entitlements server-side.
- After expiry/grace handling, preserve company data and allow the agreed renewal/support path.
- Manual payment verification and subscription extension are auditable and idempotent.
- Do not imply that payment was verified merely because a screenshot was sent.

### F. User experience and supportability
- Critical tasks should be understandable to a company operator without developer assistance.
- Provide clear loading, empty, validation, success, failure, offline/network retry, and permission-denied states.
- Prevent accidental destructive actions; require confirmation where appropriate and preserve audit history.
- Provide useful date/range filters and readable lists/statements.
- Use Arabic/RTL consistently where the product UI is Arabic; verify layout on actual supported Android screen sizes.
- Document common support procedures and a safe way to collect diagnostic information without exposing secrets.

### G. Monitoring, reliability, and recovery
- Implement the separately documented [Observability & Production Monitoring](OBSERVABILITY-ROADMAP.md) plan.
- Track request volume, latency percentiles, errors, JVM/container health, database connection-pool pressure, and application availability.
- Provide actionable alerts, safe correlated logs, and a troubleshooting runbook.
- Define backup schedule, retention, access protection, and a tested restore procedure before production launch.
- Test startup after restart, database unavailability/recovery, disk pressure, network interruption, and duplicate client retries.
- Establish practical performance baselines with representative test data and expected concurrency.
- Monitoring, backups, and restore must be tested, not merely configured.

### H. Release and update management
- Define supported app/backend versions and API compatibility policy.
- Keep Android package ID and signing key stable; test upgrading an existing installation and preserving local data.
- Test actual APK distribution/download on target devices, optional/mandatory update behavior, and minimum-version enforcement.
- Record release notes, build/version, deployment steps, rollback procedure, and release approval.
- Do not release a new version without checking backend tests, Flutter analysis/tests, migration safety, and the relevant end-to-end flows.

## 3. Commercial release gates

Treat the following as release gates, not optional polish:

1. **Core workflow:** a company can complete its real daily driver/equipment/shift workflow.
2. **Tenant isolation:** negative tests prove one tenant cannot read or mutate another tenant's data.
3. **Financial correctness:** sample driver/equipment/company cases reconcile, including retries, corrections, unpaid expenses, and month rollover where implemented.
4. **Authentication and permissions:** activation, login, recovery, logout/session revocation, disabled accounts, and role restrictions behave as specified.
5. **Operational visibility:** real monitoring dashboards and actionable alerts are verified in the target deployment.
6. **Backup and restore:** restore into a clean environment is demonstrated and checked for data integrity.
7. **Release/update:** install and upgrade are tested on a real Android device with the intended APK distribution path.
8. **Usability:** a representative operator can complete the key workflow using clear instructions and no hidden developer-only steps.
9. **Support readiness:** deployment, incident, backup/restore, and customer-support runbooks exist.
10. **Evidence:** each gate has recorded test results and an explicit pass/block status.

A release gate may be marked passed only when evidence is available. Any unresolved blocker must be stated clearly; do not silently downgrade a required gate.

## 4. Recommended delivery order

1. Stabilize and verify existing core operations and tenant isolation (current Phase 0 and Phase 1).
2. Complete financial foundation and monthly close (Phase 2).
3. Implement purchasing, suppliers, and inventory only after accounting policies are decided (Phase 3).
4. Build reports/exports against reconciled ledgers (Phase 4).
5. Complete the agreed authentication redesign and optional driver accounts (Phase 5), coordinating with current Platform Owner/activation work.
6. Validate app update and APK distribution (Phase 6).
7. Implement subscriptions and the manual-payment pilot after price/policy decisions (Phase 7).
8. Add payment gateway/push notifications later only when justified (Phase 8).
9. Complete Platform Owner/admin activation and dashboard acceptance (Phase 9).
10. Implement production observability (Phase 10).
11. Run a product release-readiness review across the gates above; do not treat phase completion alone as proof of launch readiness.

The exact ordering of parallel work can change after reviewing dependencies, but security, tenant isolation, financial correctness, backups, and operational visibility must be release gates.

## 5. Explicit non-goals for the first release unless separately approved

- Do not introduce Kubernetes or a large operations stack merely for appearance.
- Do not build multi-tenant driver identity/linking before there is a confirmed requirement.
- Do not add a payment gateway, recurring auto-renewal, or push notifications before the agreed manual pilot proves the need.
- Do not add complex roles, full offline synchronization, or AI features ahead of the essential operational and financial workflows.
- Do not claim full offline support; explicitly handle network failures and safe retries.

## 6. Product acceptance review template

For every major workflow, record:
- Scenario and actor/role.
- Preconditions and tenant.
- Expected result and expected financial/data changes.
- Failure/retry/concurrency cases.
- Automated test and manual test evidence.
- Security/privacy checks.
- Result: **Not started / In progress / Blocked / Passed**.
- Remaining risk, owner, and next action.

## 7. Relationship to other documents

- [Product Roadmap](PRODUCT_ROADMAP.md) remains the source of truth for phase order and product decisions.
- [Observability & Production Monitoring](OBSERVABILITY-ROADMAP.md) defines the detailed monitoring scope.
- [Security Roadmap](SECURITY-ROADMAP.md) defines security requirements.
- Phase-specific documents define implementation checklists and exit criteria.

**Important:** This document defines what product readiness means. It does not assert that these capabilities have already been implemented or accepted. Update statuses only after inspecting the current branch and recording real test/deployment evidence.
