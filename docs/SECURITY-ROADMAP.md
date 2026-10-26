# Contractor Manager — Security Roadmap

**Status:** Agreed plan; implementation has not started unless a task below is explicitly marked complete.  
**Agreed:** 2026-10-26  
**Scope:** Platform Owner account, tenant-company administrators (tenant `ADMIN`), authentication/recovery, authorization, tenant isolation, sessions, and security auditability.

This document records product decisions and acceptance criteria. It is not evidence that the features already exist. Inspect the current branch and tests before implementation, and update status only when code and test evidence support it.

## 1. Agreed security policy

### 1.1 Platform Owner
Platform Owner is the highest-impact account because it can create and manage tenants. Apply the stronger security baseline:

- Require username/password plus TOTP-based two-factor authentication (2FA) using a compatible authenticator app.
- Require a verified email address for recovery and security notifications; email is not the routine fallback for the second factor.
- Provide one-time recovery codes. Store protected representations where appropriate, show them only during issuance/regeneration, and invalidate used codes.
- Rate-limit authentication and second-factor attempts; use generic errors where revealing account state would help attackers.
- Do not issue a full Platform Owner access token after password verification alone. Issue a short-lived, narrowly scoped pre-authentication challenge that can only complete the 2FA step.
- Require fresh authentication for sensitive security changes and other high-impact operations where appropriate.
- Revoke relevant sessions/tokens after password, 2FA, recovery-email, or account-security changes.
- Record security-relevant events without logging passwords, raw tokens, TOTP secrets, recovery codes, or other credentials.
- Provide safe initial enrollment/bootstrap so mandatory 2FA does not permanently lock out the legitimate owner. Test lost-device and recovery paths before production enforcement.

### 1.2 Tenant Admins
- Require secure first-admin activation; activation links expire, are single-use, and are invalidated when reissued.
- Require a verified email address before it can be used for account recovery. Email provider configuration must be explicit; never claim an email was sent if delivery is not configured or fails.
- Provide secure password reset using short-lived, single-use tokens; store token hashes rather than raw tokens, invalidate old tokens appropriately, and revoke/rotate sessions according to policy.
- Offer TOTP-based 2FA to tenant admins. Make it mandatory for accounts with sensitive/high-impact permissions initially; retain a centrally controllable path to require it for all tenant admins later.
- Rate-limit login, activation, password-reset, and 2FA attempts; use generic password-reset responses to avoid account enumeration.
- Enforce role permissions in the backend. A tenant admin may access only their own tenant and must not create tenants or access another tenant by changing a request parameter.
- Audit authentication, account-recovery, permission-change, and high-impact administrative events.

### 1.3 Tenant isolation and authorization
- Derive tenant scope from the authenticated identity and trusted server-side authorization context; never trust a client-supplied `tenantId` as authorization.
- Apply tenant-scoped lookups and ownership checks throughout API/service paths, including related records.
- Keep Platform Owner privileges separate from tenant-admin privileges and prevent role escalation.
- Test direct API calls, including attempts to read, modify, delete, or reference another tenant's records.
- Use least privilege and deny by default when permission is absent.

## 2. Delivery sequence

### Security Phase A — Review the current baseline
**Status: Not started**
- Inspect auth endpoints, JWT/refresh-token lifecycle, Spring Security rules, user schema, migrations, tenant-scoped repositories, tests, and email configuration on the current branch.
- Identify session/token revocation capabilities and configuration/deployment impacts.
- Document affected files, APIs, migrations, threats, and tests before coding.
- Never edit an already-applied Flyway migration; add a new migration when needed.

**Exit criteria:** reviewed implementation plan and tests approved before code changes.

### Security Phase B — Tenant-admin email, activation and recovery
**Status: Not started**
- Add tenant-admin email capture to company/first-admin creation and a safe migration path for existing accounts without email.
- Verify email ownership before enabling recovery.
- Add configurable email delivery using deployment secrets; commit no credentials.
- Add resend-activation and password-reset endpoints/pages with short expiry, single use, hash-at-rest, token invalidation, rate limits, and generic responses.
- Define session invalidation after password reset and account-security changes.
- Allow manual copy-link fallback only when explicitly enabled for local/development and protected behind Platform Owner authorization.

**Exit criteria:** verified email works; expired/replayed/invalidated tokens fail; raw tokens are not logged; provider failures are handled safely; migration cases are tested.

### Security Phase C — Platform Owner 2FA
**Status: Not started**
- Add TOTP enrollment, confirmation, challenge verification, secure disable/re-enrollment, and recovery codes.
- Make 2FA mandatory without issuing a full Platform Owner token before successful second-factor verification.
- Protect TOTP secrets and keep secrets/codes out of API responses and logs.
- Add verified recovery email and security notifications.
- Test safe initial enrollment and lost-device recovery.
- Revoke sessions after password/2FA/recovery-channel changes and require re-authentication for sensitive actions.

**Exit criteria:** password-only login cannot obtain a full owner token; valid TOTP/recovery paths work; invalid/replayed codes fail; rate limits and recovery behavior are tested.

### Security Phase D — Tenant-admin 2FA and central policy
**Status: Not started**
- Implement TOTP enrollment/challenge for tenant admins.
- Enforce 2FA for accounts with sensitive/high-impact permissions.
- Keep enforcement centrally configurable so it can later apply to all tenant admins.
- Make enrollment, recovery, disabling, and lost-device handling safe and auditable.
- Prevent tenant admins from changing another tenant's security policy or elevating their own role.

**Exit criteria:** enforcement is server-side; alternate endpoints/refresh flows cannot bypass 2FA; recovery and policy changes are tested.

### Security Phase E — Authorization, sessions, audit and hardening
**Status: Not started**
- Audit tenant isolation and role-based authorization across all endpoints, not only the dashboard.
- Review access-token lifetime, refresh-token rotation/reuse detection, logout and revocation semantics.
- Add authentication throttling with a strategy that avoids easy denial-of-service against known users.
- Audit sign-in, recovery, 2FA/security changes, user/role changes, tenant status changes, and sensitive administrative actions.
- Keep logs free of secrets and restrict audit-log access.
- Review production HTTPS, secret management, client token storage, CORS/origin restrictions where applicable, and safe error handling.

**Exit criteria:** cross-tenant and role-escalation tests pass; session/revocation behavior is documented; sensitive events are auditable without credential leakage; deployment config is reviewed.

## 3. Minimum security test matrix
- Platform Owner password success without valid 2FA does not issue a full access token.
- Valid TOTP works; invalid or replayed one-time codes fail.
- Recovery codes are single-use and cannot be retrieved after issuance.
- Tenant-admin activation links expire, work once, and old links fail after reissue.
- Password-reset tokens expire, work once, and are invalidated according to policy.
- Reset requests return a generic response whether or not the account/email exists.
- Authentication, reset, activation and 2FA endpoints enforce rate limits.
- Password/security changes invalidate the intended sessions and refresh tokens.
- Tenant admin cannot access another tenant, create tenants, or change roles/security settings beyond their authority through direct API calls.
- Disabled accounts and revoked sessions cannot continue using protected endpoints.
- Email-provider failure does not produce a false success claim or leak raw tokens into logs.
- Existing accounts and database upgrades remain usable after migrations.

## 4. Operational rules
1. Work on the approved feature branch; do not merge into `main` without explicit approval.
2. Inspect current code before each phase and avoid unrelated refactors.
3. Share intended files/API/schema changes and tests before implementation.
4. Add Flyway migrations rather than editing migrations that may already have run.
5. Never commit secrets, recovery codes, passwords, raw tokens, or production credentials.
6. Run targeted tests and full backend CI; record exact commit and results.
7. Discussion or code presence alone is not proof of implementation or successful testing.

## 5. Decisions agreed
- Platform Owner: **mandatory TOTP 2FA**, verified email for recovery/security alerts, and one-time recovery codes; email is not the routine second-factor fallback.
- Tenant Admins: verified email for recovery; secure activation and password reset; **2FA mandatory for sensitive/high-impact permissions initially**, with a future central option to require it for every tenant admin.
- All roles: backend-enforced least privilege, tenant isolation, rate limits, appropriate session revocation, and security audit events.
- Delivery order: review baseline → tenant-admin email/activation/recovery → Platform Owner 2FA → tenant-admin 2FA → authorization/session/audit hardening.
- These are approved requirements, not claims of implementation. Implementation starts only after the current branch and existing auth design have been reviewed.
