# Phase 05 — Authentication redesign and optional driver accounts

**Roadmap:** [PRODUCT_ROADMAP.md](../PRODUCT_ROADMAP.md#phase-5--authentication-redesign-and-optional-driver-accounts)  
**Status:** Not started as a redesign phase. Existing authentication is a baseline to verify, not a claim that the target redesign is implemented.

## Current documented baseline
The existing API has been documented as including:
- `POST /api/v1/auth/register-tenant`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/change-password`
- User management under `/api/v1/users`

Re-check these paths, role checks, token/session behavior, and the current schema before modifying auth. The old public tenant-registration flow may conflict with the agreed product decision that self-registration is closed; do not change it without a reviewed implementation plan.

## Agreed direction
- Platform Owner alone creates tenants and establishes the first tenant administrator. Tenant ADMIN manages users only within their tenant and cannot create tenants or access another tenant.
- Self-service tenant registration is currently closed. Any future reopening must be an explicit platform-controlled setting with authorization and abuse-prevention tests.
- Never trust a client-supplied `tenantId` to establish authorization scope; derive tenant scope from authenticated server-side identity.
- A driver record may exist without a login account. If driver accounts are introduced, link to the existing driver record rather than creating duplicates; use a separate least-privilege `DRIVER` role with backend-enforced read-only access to permitted data.
- Preferred future login UX is normalized phone + password with stable internal user ID. The first release target assumes globally unique normalized login phones; driver contact phones are not automatically login identities.
- Plan migration from globally unique usernames carefully; handle missing/duplicate phone values and consider a temporary username fallback only after a product decision.
- Require verified email for login accounts and provide a secure single-use, expiring email recovery link initially; email provider and edge cases remain decisions to settle.
- Add rate limits, generic recovery responses, token invalidation and a documented refresh-session revocation policy.
- Preserve tenant isolation and avoid broadening driver identity to multi-tenant membership without a real product need and a reviewed migration plan.

## Checklist
- [ ] Inspect current auth controllers, services, filters, JWT/refresh-token code, role enum, users schema, migrations, and tests.
- [ ] Separate platform tenant creation from tenant-scoped user management.
- [ ] Confirm server-side tenant scoping and authorization on every relevant path.
- [ ] Decide and document the existing-user migration for phone login and email recovery before schema changes.
- [ ] Implement verified email and safe password recovery only after provider and delivery costs are agreed.
- [ ] Add optional driver account linking without duplicate driver records.
- [ ] Add tests for cross-tenant access, role escalation, disabled users/tenants, duplicate normalized phones, expired/reused recovery tokens, and revoked sessions.

## Acceptance criteria
- [ ] Only Platform Owner can create tenants; tenant ADMIN cannot cross tenant boundaries.
- [ ] Self-registration is rejected while disabled.
- [ ] Existing users have a safe migration path; no user is locked out by an unplanned identity migration.
- [ ] DRIVER permissions are least-privilege and enforced by backend authorization.
- [ ] Recovery is rate-limited, non-enumerating, single-use, expiring, and audited where appropriate.
- [ ] Relevant backend tests pass and actual results are recorded.
