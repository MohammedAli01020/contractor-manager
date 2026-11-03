# Mobile Operating Experience — Access, Notifications, Search, and Offline

**Status:** Product direction documented; individual workflows remain subject to screen review and explicit approval.  
**Platform:** Contractor Manager Flutter mobile app  
**Design baseline:** [Design System v1.0](../design/design-system/v1.0.md)

## 1. Purpose and scope

This document records the mobile operating-experience decisions discussed for contractor owners, supervisors, accountants, storekeepers, and drivers. It complements the individual screen specifications; it does not replace backend authorization, accounting rules, or implementation review.

## 2. Role-aware application

- Start with one Flutter application and role-aware navigation/screens rather than separate applications for each role.
- The contractor/tenant owner experience remains the primary workflow.
- Supervisors, accountants, storekeepers, and drivers see only the destinations and actions allowed by their permissions.
- Hiding a control in Flutter is not security enforcement. Every API operation and every record read must be authorized by the backend and tenant-scoped.
- Platform Owner Web remains a separate product surface and is not part of the tenant mobile navigation.

### 2.1 Optional driver login

- A driver login is optional, not required for every driver.
- The login is linked to the existing driver profile; it must not create a second driver identity or duplicate financial account.
- Disabling login access must not delete the driver profile, shifts, advances, payments, or statement history.
- The driver may view their own eligible shifts, wage snapshots, advances, payments, balance explanation, and statement/PDF where permitted.
- The driver may submit a shift-review request or dispute; they cannot directly edit their wage, posted financial movements, or another driver's records.
- Driver access must not expose company-wide cash/bank accounts, profit, general ledger, inventory valuation, or other drivers' private information.
- The statement must distinguish wages earned, advances, payments, and remaining balance. An advance must not be silently netted against wages without showing the movement and its treatment.

## 3. Notification center

The notification center is a role-aware inbox for relevant operational and financial events.

Candidate events include:
- new or changed shift requiring the user's attention;
- shift completion or a request to review a shift;
- wage confirmation or a driver review response;
- advance recorded and payment confirmed;
- expense or stock action requiring approval;
- low-stock or stocktake exception where configured;
- period-close checklist exception or close/reopen event;
- synchronization failure requiring user action.

Rules:
- Notifications are generated from authoritative server-side events, not from an optimistic local assumption.
- A payment/receipt notification must not say that money was successfully recorded until the server confirms the transaction.
- Each notification deep-links to the source record, subject to the recipient's permissions.
- Preferences may control in-app, push, and optional email channels by event/category.
- Avoid duplicate notifications on retries; use stable event identifiers and idempotent delivery.
- Keep read/unread state and relevant delivery/status history.
- Do not expose sensitive financial details in lock-screen text by default; provide privacy-aware summaries.

## 4. Global search

- Provide a search entry point that can find records the current user is authorized to access.
- Searchable candidates include shifts, drivers, equipment, expenses, advances, receipts/payments, inventory items/movements, documents, and parties when those modules exist.
- Results show a concise type label, identifying reference, date/status, and a useful summary.
- Selecting a result opens the original source record; search results are not editable duplicate records.
- Search must enforce tenant boundaries and the same record-level permissions as normal navigation.
- Do not reveal the existence or metadata of inaccessible records through suggestions, counts, or error messages.
- Filters and ranking should favor exact identifiers/references and recent relevant records without changing source data.

## 5. Offline-first architecture and offline-aware UX

### 5.1 Adopted architectural direction

The selected product direction is:

**Offline-first Architecture + Offline-aware User Experience.**

Offline-first is the broader architecture and already includes offline support; Offline Support is not tracked as a separate competing architecture goal. Offline-aware describes how the app communicates connection, local persistence, synchronization, and failure states to the user.

The app should be designed so that local data and local persistence are first-class parts of the workflow, while the server remains authoritative for shared business state, permissions, and posted financial truth.

### 5.2 User-visible operation states

The UI must distinguish at least:

1. **Draft (local):** entered and saved on this device, not yet submitted.
2. **Pending sync:** queued and waiting to be sent.
3. **Syncing:** currently being transmitted/processed.
4. **Synced / confirmed:** server accepted and persisted the operation.
5. **Sync failed:** operation is retained locally; show a useful reason and safe retry action.
6. **Conflict / needs review:** server state differs or a business rule prevents automatic application; preserve the user's data and require an explicit resolution path.

Do not label a local draft or pending operation as a completed server transaction. Show timestamps and the relevant device/account context where helpful.

### 5.3 Local persistence and synchronization

- Persist eligible local drafts and queued operations across app restarts and device restarts.
- Use stable client-generated operation identifiers/idempotency keys so retries cannot create duplicate shifts, movements, documents, or journal entries.
- Synchronization must be retry-safe, ordered where business dependencies require it, and resilient to intermittent connectivity and app termination.
- The server validates authentication, current permissions, tenant ownership, business rules, period locks, and concurrency before accepting an operation.
- Preserve both the local operation state and the server response/reference for troubleshooting.
- Do not silently overwrite newer server data with stale local values.
- Conflicts must have explicit policies per entity/action; financial or historical records must not use blind last-write-wins.
- Provide a synchronization center/list showing pending, failed, and conflict items, with retry or review actions where safe.
- Background synchronization may be used where supported, but the app must also provide a clear foreground/manual retry path.
- Sign-out, account switching, tenant switching, and device loss require a defined policy for protecting and reconciling queued private data. Never upload one user's queued operations under another user's identity.

### 5.4 Initial offline operation policy

Offline capability should be enabled progressively by risk, not by assuming every operation is safe offline.

Initial candidate capabilities:
- view previously synchronized/cached records with a visible last-updated time;
- create and edit local drafts;
- capture eligible operational shift data for later submission, subject to server validation on sync;
- prepare notes and supporting evidence locally where secure storage and upload rules permit.

Initially require server confirmation before treating these as completed:
- cash/bank receipts and payments;
- driver advances and other posted financial movements;
- journal posting, accounting corrections, and period close/reopen;
- actions requiring current shared balances, approvals, or locked-period checks.

The exact offline action matrix must be finalized against backend APIs, accounting policy, permissions, and conflict behavior before implementation.

### 5.5 Financial and audit integrity

- The server is authoritative for posted ledger entries, balances, stock balances, permissions, and period status.
- Never show an unconfirmed financial operation as a successful receipt, payment, advance, or posted journal entry.
- Never allow retries to duplicate a ledger entry or stock movement.
- Historical posted transactions are corrected through explicit reversal/adjustment workflows, not silent local overwrites.
- All accepted operations must retain actor, tenant, timestamps, source identifiers, and audit history.
- Multi-instance deployments must preserve idempotency and concurrency guarantees at the database/backend layer, not only in the Flutter client.

## 6. Connection and synchronization presentation

Every relevant screen should communicate:
- whether the device is online or offline;
- whether displayed data is current, cached, or stale;
- whether a change is a local draft, queued, syncing, confirmed, failed, or conflicted;
- what the user can safely do next.

Use concise Arabic-first labels and text plus icons; color must not be the only indicator. A network error must not erase entered data. Avoid generic “success” messages before server confirmation.

## 7. Acceptance criteria

- One app presents role-appropriate navigation while backend authorization remains authoritative.
- Optional driver access is linked to one existing driver profile and exposes only that driver's permitted information.
- Notifications deep-link to authorized source records and are not duplicated by retries.
- Global search never leaks cross-tenant or unauthorized records.
- Local drafts and eligible queued operations survive app restarts.
- Every operation has a stable idempotency identity and cannot be duplicated by repeated synchronization.
- The UI distinguishes local persistence from server confirmation.
- Failed operations retain user data and provide safe retry or review.
- Conflicts do not silently overwrite server or financial history.
- Cash/bank postings and period close are not represented as complete before server confirmation.
- Offline and synchronization behavior is tested on real Android devices under intermittent connectivity.

## 8. Open decisions before implementation

1. Exact list of operations allowed offline in the first release.
2. Local database and secure-storage technology for Flutter.
3. Cache freshness, retention, encryption, and device logout/account-switch policy.
4. Conflict resolution policy per entity and field.
5. Background sync scheduling and attachment upload limits.
6. Driver login invitation, credential recovery, and account lifecycle.
7. Notification event catalog, user preferences, push provider, and retention.
8. Search indexing strategy and supported filters.
9. Whether supervisors can submit shifts offline and which validations can safely be deferred to the server.

These items require API/backend design, security review, and explicit product-owner approval before production implementation.
