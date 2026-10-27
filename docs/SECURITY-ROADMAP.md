# Contractor Manager — Security Roadmap

**Status:** Agreed plan; implementation has not started unless a task below is explicitly marked complete.  
**Agreed:** 2026-10-26  
**Scope:** Platform Owner account, tenant-company administrators (tenant `ADMIN`), authentication/recovery, authorization, tenant isolation, sessions, and security auditability.

This document records product decisions and acceptance criteria. It is not evidence that the features already exist. Inspect the current branch and tests before implementation, and update status only when code and test evidence support it.

## 1. Agreed security policy

### 1.1 Platform Owner (platform-level administrator)

Platform Owner is the highest-impact account because it can create and manage tenants. Apply the stronger security baseline:

- Require username/password plus TOTP-based two-factor authentication (2FA) using a compatible authenticator app.
- Require a verified email address for recovery and security notifications; email is not the routine fallback for the second factor.
- Provide one-time recovery codes. Store only protected representations where appropriate, show them only during issuance/regeneration, and invalidate used codes.
- Rate-limit authentication and second-factor attempts; use generic errors where revealing account state would help attackers.
- Do not issue a full Platform Owner access token after password verification alone. Issue a short-lived, narrowly scoped pre-authentication challenge that can only complete the 2FA step.
- Require fresh authentication for sensitive security changes and other high-impact operations where appropriate.
- Revoke relevant sessions/tokens after password, 2FA, recovery-email, or account-security changes.
- Record security-relevant events without logging passwords, raw tokens, TOTP secrets, recovery codes, or other credentials.
- Provide a safe initial enrollment/bootstrap process so mandatory 2FA does not permanently lock out the legitimate owner. Test lost-device and recovery paths before enforcing the policy in production.

### 1.2 Tenant Admins (company administrators)

Use strong protection without unnecessarily blocking ordinary company operations:

- Require a secure activation flow for the first tenant administrator; activation links must expire, be single-use, and be invalidated when reissued.
- Require a verified email address before it can be used for account recovery. Email delivery/provider configuration must be explicit; never claim an email was sent if delivery is not configured or fails.
- Provide a secure password-reset flow using short-lived, single-use tokens; store token hashes rather than raw tokens, invalidate previous/reset tokens appropriately, and revoke or rotate sessions according to the agreed policy.
- Offer TOTP-based 2FA to tenant admins. Make it mandatory for accounts with sensitive/high-impact permissions initially; keep a centrally controllable path to make it mandatory for all tenant admins as the product grows.
- Rate-limit login, activation, password-reset, and 2FA attempts; use generic password-reset responses to avoid account enumeration.
- Enforce role permissions in the backend. A tenant admin may access only their own tenant and must not create tenants or access another tenant by changing a request parameter.
- Record relevant authentication, account-recovery, permission-change, and high-impact administrative events.

### 1.3 Tenant isolation and authorization

Tenant isolation is a security invariant, not a UI feature:

- Derive tenant scope from the authenticated identity and trusted server-side authorization context; never trust a client-supplied `tenantId` as authorization.
- Apply tenant-scoped lookups and ownership checks in service/API paths, including nested resources and related records.
- Keep Platform Owner privileges separate from tenant-admin privileges and avoid accidental role escalation.
- Test direct API calls as well as UI flows, including attempts to read, modify, delete, or reference another tenant's records.
- Use least privilege for each role and deny by default when a permission is absent.

## 2. Delivery sequence

### Security Phase A — Review the current baseline
**Status: Complete (source review only; no application code changed)**

- Inspect the current branch's auth endpoints, JWT/refresh-token lifecycle, Spring Security rules, user schema, migrations, tenant-scoped repository methods, and existing tests.
- Identify current session/token revocation capabilities and the email delivery configuration.
- Document the API, schema, migration, configuration, and deployment impacts before coding.
- Never edit an already-applied Flyway migration; add a new migration where needed.

**Exit criteria:** reviewed implementation plan, list of affected files/endpoints, migration strategy, threat cases, and tests approved before code changes.

### Security Phase B — Tenant-admin email, activation and recovery
**Status: Not started**

- Add tenant-admin email capture to the company/first-admin workflow and a safe migration strategy for existing accounts with no email.
- Verify email ownership before enabling recovery.
- Add configurable email delivery using deployment secrets; no credentials committed to source control.
- Add resend-activation and password-reset endpoints/pages with short expiry, single use, hash-at-rest, token invalidation, rate limits, and generic responses.
- Define session invalidation after password reset and account-security changes.
- Preserve the manual copy-link fallback for local/development environments only when explicitly enabled and protect it behind Platform Owner authorization.

**Exit criteria:** verified email lifecycle works; expired/replayed/invalidated tokens fail; reset links are not exposed in logs; email-provider failures are handled safely; tests cover existing users and migration behavior.

### Security Phase C — Platform Owner 2FA
**Status: Not started**

- Add TOTP enrollment, confirmation, challenge verification, disable/re-enrollment protection, and recovery codes.
- Make 2FA mandatory for Platform Owner without issuing a full access token before successful second-factor verification.
- Secure TOTP secret storage and prevent secrets/codes from appearing in API responses or logs.
- Add verified recovery email and security notifications.
- Add safe initial enrollment and tested recovery/lost-device procedures.
- Revoke sessions after password/2FA/recovery-channel changes and require re-authentication for sensitive actions.

**Exit criteria:** password-only login cannot obtain a full Platform Owner token; valid TOTP/recovery paths work; invalid/replayed codes fail; rate limits and recovery behavior are tested; legitimate owner can recover without weakening routine login.

### Security Phase D — Tenant-admin 2FA and central policy
**Status: Not started**

- Implement TOTP enrollment/challenge for tenant admins.
- Enforce 2FA for accounts with sensitive/high-impact permissions.
- Keep the enforcement policy centrally configurable so it can later be applied to all tenant admins.
- Make enrollment, recovery, disabling, and lost-device handling safe and auditable.
- Do not let a tenant admin change another tenant's security policy or elevate their own role.

**Exit criteria:** enforcement is server-side; users cannot bypass 2FA through alternate endpoints/refresh flows; recovery and policy changes are tested.

### Security Phase E — Authorization, sessions, audit and hardening
**Status: Not started**

- Audit tenant isolation and role-based authorization across all endpoints, not only the dashboard.
- Review access-token lifetime, refresh-token rotation/reuse detection, logout and revocation semantics.
- Add authentication throttling/lockout strategy that avoids easy denial-of-service against known users.
- Add audit events for sign-in failures/successes, recovery, 2FA/security changes, user/role changes, tenant status changes, and sensitive administrative actions.
- Keep audit logs free of secrets and protect their access.
- Add production deployment checks: HTTPS, secret management, secure cookie/storage decisions for the actual clients, CORS/origin restrictions where applicable, and safe error handling.

**Exit criteria:** cross-tenant and role-escalation tests pass; session/revocation behavior is documented; sensitive events are auditable without leaking credentials; deployment configuration is reviewed.

## 3. Minimum security test matrix

- Platform Owner password succeeds but missing/invalid 2FA does not issue a full access token.
- Valid TOTP succeeds; invalid, expired where applicable, or replayed one-time codes are rejected.
- Recovery codes are single-use and cannot be retrieved again after issuance.
- Tenant-admin activation links expire, work once, and old links fail after reissue.
- Password-reset tokens expire, work once, and old tokens are invalidated according to policy.
- Reset requests return a generic response whether or not the account/email exists.
- Authentication, reset, activation and 2FA endpoints enforce rate limits.
- Password/security changes invalidate the intended sessions and refresh tokens.
- A tenant admin cannot access another tenant's records, create tenants, or modify roles/security settings outside their authority through direct API calls.
- Disabled/inactive accounts and revoked sessions cannot continue using protected endpoints.
- SMTP/provider failure never yields a false success claim or leaks raw reset/activation tokens into logs.
- Existing accounts and database upgrades remain usable after migrations.

## 4. Operational and implementation rules

1. Work on the approved feature branch; do not merge into `main` without explicit approval.
2. Inspect current code before each phase and avoid unrelated refactors.
3. Share the intended files/API/schema changes and tests before implementation.
4. Add new Flyway migrations rather than editing migrations that may have run.
5. Never commit secrets, recovery codes, passwords, raw tokens, or production credentials.
6. Run targeted tests and the full backend CI suite; record exact commit and results.
7. Keep the roadmap statuses honest: discussion or code presence alone is not proof of implementation or successful testing.

## 5. Decisions agreed

- Platform Owner: **mandatory TOTP 2FA**, verified email for recovery/security alerts, and one-time recovery codes; email is not the routine second-factor fallback.
- Tenant Admins: verified email for recovery; secure activation and password reset; **2FA mandatory for sensitive/high-impact permissions initially**, with a future central option to require it for every tenant admin.
- All roles: backend-enforced least privilege, tenant isolation, rate limits, appropriate session revocation, and security audit events.
- Delivery order: review baseline → tenant-admin email/activation/recovery → Platform Owner 2FA → tenant-admin 2FA → authorization/session/audit hardening.
- These are approved requirements, not claims of implementation. Implementation starts only after the current branch and existing auth design have been reviewed.


## 6. Consolidated execution plan — approved 2026-10-27

This section consolidates the approved security roadmap with the Platform Owner company/account-management workflow. Approval means the requirements below are accepted; it does not mean implementation is complete.

### 6.1 Platform Owner company and user administration

- Keep a central list of all tenants with status and the primary tenant-admin summary.
- Add a tenant-scoped view of user accounts for a selected company, showing only necessary profile/role/activation/status fields. Never expose password hashes, reset tokens, TOTP secrets, recovery codes, or other credentials.
- Keep Platform Owner actions separate from tenant-admin capabilities and enforce authorization in backend endpoints, not only in the UI.
- Record sensitive administrative actions in the audit log.
- Provide a controlled resend-activation action for a tenant admin who has not activated their account. Issuing a new activation token must invalidate prior tokens.
- Provide a separate password-reset request for an already activated tenant admin. Do not let the Platform Owner read or choose the user's password, or view a reset token in production.
- Prefer delivery to the account's verified email address. A manual copy-link fallback is permitted only in explicitly enabled local/development environments, protected by Platform Owner authorization, and must never be silently enabled in production.

### 6.2 Password recovery and email delivery

- Confirm email-provider configuration and ownership verification before enabling recovery for an account.
- Password-reset tokens must be cryptographically random, short-lived, single-use, stored as hashes, and invalidated on reissue/use.
- Return generic responses that do not disclose whether an account/email exists.
- Rate-limit reset and resend endpoints and avoid placing raw tokens in logs or audit events.
- Do not report successful delivery if the email provider is missing or delivery fails; handle provider errors safely.
- Define and test refresh-token and access-session invalidation after password reset and other account-security changes.

### 6.3 Delivery sequence and status

1. **Phase A — Baseline review:** complete for the source areas listed below. The latest feature branch head is `7bbd41d2ddc17327cb02fd9a29b53343ab0c2202`; compared with the previously reported local commit `ff2b567f15ebb41adfd6519d697225cc5d3de685`, it is ahead by two documentation-only commits and has no application-code diff. The branch contains migrations V1–V11, with V11 as the latest migration. `User` has no email field; `AuthService` has login, refresh, logout, and authenticated password change but no reset flow; `PlatformAdminService` creates activation links but has no resend/reset flow; activation tokens are hash-only, expiring, single-use and locked on consumption. `SecurityConfig` disables CSRF and permits auth/activation routes; this is a hardening review item, not by itself proof of an exploitable issue. The tenant repository has tenant-scoped user lookups, and existing integration tests cover activation lifecycle, disabled owners, and tenant/role separation. Email delivery is not configured in the inspected `application.yml` or backend dependencies. No application tests were run during this source-only review; the latest commit is documentation-only and has no reported status checks.
2. **Phase B — Tenant-admin email, activation and password recovery:** implementation design prepared below; application code has not changed. Use a new forward-only Flyway migration (V12 unless a fresh branch check reveals a later migration), keep email nullable for existing accounts, and require verified email before recovery.
3. **Phase C — Platform Owner mandatory TOTP 2FA and recovery codes:** pending.
4. **Phase D — Tenant-admin TOTP 2FA and central enforcement policy:** pending.
5. **Phase E — Account administration, tenant isolation, sessions, rate limits, CSRF/origin review, and audit hardening:** pending; includes the user-list view and controlled resend-activation/reset actions.
6. **Verification and release readiness:** run focused backend tests, full backend tests/CI, frontend lint/build where affected, and record exact commit and results. Do not merge into `main` without explicit approval.

### 6.4 Implementation gate

Before each implementation phase, publish the exact affected files, API endpoints, migration/configuration impact, threat cases, and tests. Do not edit migrations that may already have been applied. Preserve local and remote progress, do not reset or delete the existing database, and do not claim completion until code and test evidence support it.


#### Phase B implementation design — proposed file/API scope

**Backend files expected to change**

- `backend/pom.xml`: add the Spring Boot mail dependency only if the selected delivery implementation uses SMTP.
- `backend/src/main/resources/application.yml`: add explicit environment-backed mail and recovery/activation configuration; never commit provider credentials.
- `backend/src/main/resources/db/migration/V12__user_email_and_recovery_tokens.sql` (name/number to be rechecked immediately before implementation): add nullable `users.email`, `users.email_verified` defaulting false, and token tables for email verification/password reset. Do not alter V1–V11.
- `backend/src/main/java/com/contractor/app/user/domain/User.java` and `UserRepository.java`: add normalized email/verification fields and safe lookup methods; do not add unscoped post-login user lookups.
- `backend/src/main/java/com/contractor/app/platform/api/dto/CreateTenantRequest.java`, `CreateTenantResponse.java`, `PlatformController.java`, and `PlatformAdminService.java`: capture the first admin's email, deliver activation to that address, and add owner-authorized resend-activation. Reissue must revoke older activation tokens. A raw activation URL may be returned only behind an explicit development-only setting; production responses must not expose it.
- `backend/src/main/java/com/contractor/app/auth/api/AuthController.java`, `AuthService.java`, and new request/response DTOs: add generic password-reset request and token-confirmation endpoints, plus authenticated email-verification request/confirmation for existing users without a verified email.
- New email-delivery service/configuration, token domain/repository classes, and safe token-generation/expiry helpers; tokens stored as hashes, never logged.
- `platform-owner-web/app/platform-owner-app.tsx` and `platform-owner-web/app/api/platform/tenants/route.ts`: collect admin email, display delivery outcome safely, and add a tenant-scoped user list/resend-activation action without exposing secrets. Add/reset UI only where supported by the API contract.
- Backend integration tests for migration compatibility, email verification, activation reissue invalidating old links, reset generic responses, expiry/replay, tenant boundaries, mail-not-configured/provider-failure behavior, and session revocation after reset.

**Proposed API contract**

- `POST /api/v1/auth/password-reset/request`: accept an email; return the same generic response whether the email is absent, unverified, or unknown. Do not return a token.
- `POST /api/v1/auth/password-reset/confirm`: accept a one-time token and new password; reject expired/replayed/revoked tokens and revoke the user's refresh sessions.
- `POST /api/v1/auth/email-verification/request` and `POST /api/v1/auth/email-verification/confirm`: verify email ownership before it can be used for recovery. Existing users with null email remain able to sign in but cannot recover through email until verification is complete.
- `POST /api/v1/platform/tenants/{tenantId}/admin-activation/resend`: Platform Owner only; reject invalid tenant/account state safely, invalidate earlier activation links, and send the replacement to the saved admin email.
- `GET /api/v1/platform/tenants/{tenantId}/users`: Platform Owner only; return profile, role, activation, active status and email-verification status only, never credentials or tokens.

**Threat cases and verification gates**

- Prevent email/account enumeration; reject unverified-email recovery; bind verification/reset tokens to the intended user and purpose; enforce expiry, one-time use, invalidation on reissue, and tenant/user ownership.
- Apply request throttling to reset, verification, and resend flows before production enablement. Do not claim a message was sent if mail is unconfigured or delivery fails. Keep raw tokens out of logs, audit payloads, and production responses.
- Preserve current sign-in and existing user records during migration. Test migration from a populated pre-V12 schema and ensure old accounts are not automatically marked as email-verified.
- Do not modify `main`; do not reset or delete the existing database. No application code has been changed as part of Phase A.
