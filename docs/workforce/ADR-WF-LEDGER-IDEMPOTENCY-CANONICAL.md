# ADR-WF-LEDGER-IDEMPOTENCY-CANONICAL

**Status:** Accepted design direction; implementation not started  
**Date:** 2026-11-09  
**Branch:** `feature/workforce-ledger-unification`

## Context

The Workforce ledger is the canonical people-facing ledger, but the current financial write authority remains `driver_ledger_entries`. Both the legacy Driver API and canonical Workforce API currently converge on `DriverLedgerService`. The shared idempotency reservation uses scope `DRIVER_LEDGER`, and its fingerprint includes the Driver ID.

This preserves cross-route retries while the Driver projection exists, but it makes the Driver projection part of the financial command identity. It must not be carried into the final Workforce-native design.

The existing `financial_idempotency_keys` table is tenant-scoped and keyed by `(tenant_id, operation_scope, request_key)`. Reservations are inserted and locked in the same transaction as the journal and ledger writes. The fingerprint is a SHA-256 digest of length-prefixed business fields. These guarantees must remain.

## Decision

Introduce a canonical Workforce ledger idempotency scope for all future financial commands:

- Scope: `WORKFORCE_LEDGER`.
- Identity in the fingerprint: tenant-scoped `workforce_member_id`, never Driver ID.
- Include the normalized operation kind, effective transaction date, normalized amount, description, payment account ID, and advance type where applicable.
- Keep the request key as a tenant-scoped client-supplied idempotency token.
- All supported HTTP entry points (Workforce and legacy Driver compatibility routes) must resolve the same canonical member and call one command boundary. The legacy route must not have its own reservation path.

The operation kind remains part of the fingerprint so reusing one key for payment, earned advance, or recoverable advance conflicts deterministically. The account ID remains part of the fingerprint so a retry cannot silently redirect cash movement to a different bank/cash account.

## Compatibility with existing reservations

Do not rewrite or delete existing `DRIVER_LEDGER` reservation rows. Their stored fingerprints contain Driver IDs and the table does not retain the original request fields needed to transform those hashes offline.

During the compatibility window:

1. Resolve the tenant-scoped canonical member and its current Driver projection, if one exists.
2. Before creating a canonical reservation, look for an existing `DRIVER_LEDGER` reservation for the same tenant and request key.
3. If found, compute the legacy fingerprint using the same historical algorithm and current Driver ID. A matching fingerprint may replay the existing legacy ledger movement; a mismatch must conflict. This is compatibility replay only, not a new write in the old scope.
4. If no legacy reservation exists, reserve `WORKFORCE_LEDGER` using the canonical member fingerprint.
5. All new requests from both API routes use the same canonical scope and fingerprint. This makes mixed-route concurrent requests serialize on the same primary key.
6. Keep old reservation rows as audit/idempotency history through the agreed retention period. Do not bulk-delete them as part of the ledger cutover.

The compatibility replay lookup is only possible while the legacy Driver projection exists. Before retiring it, define and enforce an explicit retry-retention window. After that window expires, old Driver-scoped keys may be treated as expired according to a documented API contract; they must not be guessed or silently mapped to a different person.

## Journal and ledger identity

- Keep existing journal posting keys stable during the initial idempotency migration (for example, the current payment and advance key prefixes). Posted journals are immutable and their source references are audit data.
- Do not change journal posting keys and ledger request-key semantics in the same migration.
- The canonical Workforce ledger row stores the canonical member ID. Legacy Driver provenance fields remain nullable and are populated only for mirrored historical/compatibility movements.
- Idempotency reservation, journal posting, and the authoritative ledger insert remain one database transaction.
- No application dual-write is allowed. During the compatibility stage, the V56 database mirror remains the only Driver-to-Workforce copy mechanism.

## Required implementation sequence

1. Add a read-only compatibility lookup for old Driver-scoped reservations, with tests for matching retry, mismatching payload, missing legacy reservation, and tenant isolation.
2. Introduce canonical fingerprint construction and reserve `WORKFORCE_LEDGER` in the shared command boundary. Route both Workforce and legacy endpoints through that boundary in one coordinated release.
3. Add concurrency tests for mixed-route requests with the same key and different payloads, plus cross-operation key reuse (payment vs each advance type).
4. Prove transaction rollback removes the canonical reservation when journal or ledger posting fails.
5. Keep legacy Driver ledger writes authoritative until the native Workforce writer is separately implemented and reconciled.
6. After cutover, preserve append-only journal/ledger behavior and verify all old supported clients are either migrated or inside the explicit compatibility window.

## Acceptance tests

- Same tenant + member + operation + key + payload, from either API route: one movement and one journal; both retries return the original result.
- Same key with a different member, operation, amount, date, description, account, or advance type: conflict.
- Same key in different tenants: independent reservations, subject to tenant isolation.
- Old `DRIVER_LEDGER` reservation + matching legacy fingerprint: replay the original movement without creating a new canonical movement.
- Old reservation + changed payload or changed legacy identity: conflict.
- Two different keys for the same member: serialize on the financial mutex, not on unrelated idempotency rows.
- Journal posting or ledger insert failure: reservation, journal, and ledger movement roll back atomically.
- No native Workforce writer, V56 removal, or legacy table retirement is included in this ADR.

## Explicit non-actions

This ADR does not authorize:

- Switching the authoritative write ledger.
- Introducing application dual-write.
- Removing the V56 mirror trigger.
- Deleting or rewriting existing idempotency reservations.
- Dropping Driver ledger tables, Driver projections, or legacy API routes.
- Changing `main`, merging a PR, or deploying.
