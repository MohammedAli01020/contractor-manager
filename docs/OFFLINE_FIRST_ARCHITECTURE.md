# Offline-first Architecture — Contractor Manager

**Status:** Product/architecture direction agreed; implementation not started.  
**Decision date:** 2026-10-31  
**Applies to:** Flutter mobile client, Spring Boot API, and MySQL persistence.  
**Related roadmap:** [PRODUCT_ROADMAP.md](PRODUCT_ROADMAP.md)

## 1. Decision and product intent

Contractor Manager will follow an **offline-first mobile architecture backed by a centralized cloud backend**.

The Flutter app should let an authorized user continue supported day-to-day work when connectivity is unavailable or unreliable, then synchronize accepted local work with the server when connectivity returns.

This is not a move to a standalone/local-only product. The existing server remains the authoritative system for shared company data, authorization, financial validation, and final posting.

**Technology direction remains unchanged:**
- Flutter for the Android mobile application.
- SQLite (with a suitable Flutter persistence layer, to be selected after inspecting current dependencies) for the on-device working store and synchronization queue.
- Spring Boot for APIs, business rules, authorization, and synchronization endpoints.
- MySQL as the central durable database.
- Next.js remains the separate Platform Owner web application.

No microservices migration is required for this direction.

## 2. Why this fits the product

The primary user may work at construction sites where mobile connectivity is weak or intermittent. Core workflows such as shifts, equipment activity, driver records, and day-to-day expenses should not stop merely because the phone temporarily loses internet access.

Offline-first is therefore a product reliability capability, not just a performance optimization. It must not compromise tenant isolation, financial correctness, or auditability.

## 3. Architecture responsibilities

### Flutter application
- Read and write supported operational data through the local database.
- Clearly show whether data is local, waiting to sync, synchronized, or requires attention.
- Keep a durable outbox of user operations until the server confirms them.
- Retry safely after network loss, app termination, or process restart.
- Avoid presenting a locally queued financial operation as finally posted.

### Local database
- Store only the tenant data and history needed for the supported offline workflows.
- Persist pending operations and their stable operation identifiers.
- Preserve dependency/order information when one operation relies on another.
- Protect sensitive local data; select and verify an encryption/key-management approach before production implementation.
- Apply a documented logout, account-switch, and device-data retention policy.

### Sync engine
- Upload pending operations and download server changes.
- Use stable idempotency keys so retries cannot create duplicate server-side effects.
- Track acknowledgements and server-generated identifiers/versions.
- Handle partial failures and retry transient errors without losing queued work.
- Surface permanent validation or conflict errors for user resolution; never silently discard them.

### Spring Boot and MySQL
- Remain authoritative for authentication, authorization, tenant boundaries, validation, shared state, and final financial posting.
- Provide safe incremental synchronization contracts (for example, change cursors or version-based pulls) when the implementation phase is designed.
- Enforce idempotency on the server, not only in Flutter.
- Record audit metadata for operations that affect business or financial records.

## 4. Synchronization and consistency rules

1. **Durable local queue:** A locally accepted operation must survive app closure, device restart, and temporary network loss.
2. **At-least-once delivery, idempotent effects:** The client may retry; the server must guarantee that the same operation identifier does not apply the business effect twice.
3. **Server acknowledgement:** Mark an operation synchronized only after receiving a successful server acknowledgement and storing the resulting server identity/version.
4. **Tenant isolation:** Every downloaded record and uploaded operation is scoped to the authenticated tenant and authorized user. Never trust a client-supplied tenant identifier as authorization.
5. **Dependency ordering:** An operation that depends on a locally created record must not be uploaded before its prerequisite is accepted or mapped to a server identifier.
6. **Conflict handling:** Do not use blind last-write-wins for important business records. Define conflict behavior by entity/workflow before enabling offline edits. Preserve enough base-version information to detect stale updates.
7. **Deletion:** Use explicit, synchronizable deletion/tombstone semantics where required; do not infer deletion from a missing item in a partial download.
8. **Retries:** Transient network/server failures are retryable with bounded backoff. Validation, permission, and business-rule failures require a visible actionable state rather than endless retries.
9. **Clock safety:** Device timestamps are useful metadata but must not be trusted as the sole ordering or authorization mechanism. Server timestamps and versions govern shared state.
10. **Observability:** Record operation IDs and request/correlation IDs so failed sync attempts can be diagnosed without logging secrets or sensitive payloads.

## 5. Financial and inventory safety rules

Financial correctness takes priority over offline convenience.

- A locally queued journal or financial transaction is **pending synchronization**, not a posted ledger entry.
- The server remains responsible for validating balanced journal lines, account ownership, tenant scope, permissions, posting period, and idempotency.
- Do not calculate or expose a final shared balance solely from unconfirmed local financial operations. If provisional totals are shown, label them clearly as pending estimates.
- Posted financial movements must not be silently edited or deleted. Use audited correction/reversal workflows.
- Cash, driver advances/payments, equipment expenses, inventory issues, and period closing need explicit retry and duplicate-prevention rules.
- Period close, journal posting where policy requires central validation, subscription/payment administration, user/role changes, and other high-impact actions may require an online connection. Each workflow must be classified before implementation.
- Inventory quantity and availability must not be represented as globally final while offline changes from other devices may be unknown.

## 6. Offline capability rollout

Implement in stages rather than converting the entire application at once.

### Stage A — Offline readiness during current feature development
- Give new write operations stable client-generated operation IDs where appropriate.
- Design create/update flows so retries can be made idempotent.
- Keep business operations and financial posting behind backend services.
- Identify each feature's minimum required local reference data and its conflict/authorization rules.
- Avoid introducing API behavior that assumes a permanent connection.

### Stage B — Local persistence and synchronization foundation
- Inspect existing Flutter state management, networking, models, and package constraints before choosing the SQLite library.
- Define local schema, schema migrations, outbox, sync cursor/version model, and server API contracts.
- Implement authentication/session handling for offline startup without weakening server authorization.
- Implement connectivity-independent local reads, durable queued writes, sync status, and safe retry.
- Add account/tenant switching and logout cleanup rules.

### Stage C — Pilot operational workflows
Prioritize a narrow end-to-end pilot, selected after reviewing the current application:
- Reference data needed at the worksite (for example, drivers, equipment, and sites).
- Shift/operational records.
- Clearly classified operational expenses if their accounting implications are safe.
- Explicit pending/synced/error states and conflict recovery.

Expand only after the pilot passes failure and recovery tests.

### Stage D — Financial and inventory workflows
Enable each financial or inventory workflow only after its server-side idempotency, audit, conflict, and reconciliation rules are implemented and tested. Keep final posting and shared balances server-authoritative.

### Stage E — Production readiness
Test airplane mode, intermittent connectivity, app force-close, device restart, token expiry, logout/account switch, duplicate retries, partial batches, server validation errors, stale reference data, and simultaneous edits from multiple devices. Verify that no accepted local operation is lost and no business effect is duplicated.

## 7. Data scope and retention

Do not copy the entire server database to every phone by default.

For each entity, define:
- Whether it is available offline.
- Which fields are required.
- Whether the device may create, edit, or only read it offline.
- How much history is retained locally.
- Whether the data is sensitive and how it is protected.
- How stale data is presented.
- What happens when the user loses permission or changes tenant.

Retention windows and exact offline permissions are **not decided in this document**. They must be agreed per workflow based on real operational needs, device storage, privacy, and security.

## 8. Decisions still required before implementation

These must be resolved through codebase inspection and focused design before the sync engine is built:

1. Select the SQLite persistence package compatible with the repository's Flutter/Dart version and current dependencies.
2. Define the initial offline entity/workflow scope and per-entity local retention.
3. Define server sync APIs, cursor/version semantics, batch limits, and idempotency storage/retention.
4. Define conflict resolution per entity, including simultaneous edits from two devices.
5. Define offline permission behavior when a user's role or access is revoked while their device is disconnected.
6. Define local encryption, key storage, backup exclusion, logout cleanup, and lost-device response.
7. Define which operations require online approval or server confirmation.
8. Define how provisional operational totals are displayed without confusing them with posted accounting balances.
9. Define recovery and support procedures for permanently rejected or stuck operations.

Do not guess these policies during implementation.

## 9. Acceptance criteria

Offline-first is not complete merely because previously downloaded screens open without internet.

The implementation is acceptable only when tests demonstrate that:
- Supported records can be read and supported operations can be queued while offline.
- Queued operations survive app termination and device restart.
- Reconnection synchronizes queued work without loss or duplicate effects.
- Repeated upload of the same operation is idempotent.
- Dependencies and ordering are preserved.
- Stale edits and multi-device conflicts are detected and handled according to documented rules.
- Tenant boundaries and role restrictions remain enforced by the server.
- Financial and inventory operations cannot silently corrupt balances or quantities.
- Users can identify pending, synchronized, rejected, and conflict states and take the appropriate action.
- Logout, account switching, and revoked access do not expose another tenant's local data.

## 10. Current implementation status

**Agreed:** Offline-first mobile direction with centralized Spring Boot/MySQL authority.  
**Implemented:** No offline database, outbox, sync engine, or offline API contract is claimed by this decision document.  
**Next action:** Continue the current accounting foundation while applying Stage A readiness rules. After core operational workflows and API contracts are stable, inspect the Flutter codebase and design the first end-to-end offline pilot.  
**Release gate:** Do not advertise offline write capability until the relevant workflows pass the acceptance tests above.
