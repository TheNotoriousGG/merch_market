# Inventory invariants

Status: accepted implementation guardrails for stage 8. Full scope and acceptance criteria are in `docs/requirements/INVENTORY_VERTICAL_SLICE.md`.

## Balance and movement

- Quantities are whole non-negative units represented by `long`/PostgreSQL `bigint`.
- `available = onHand - reserved`; always `0 <= reserved <= onHand`.
- Missing balance means zero availability, never unlimited stock.
- Every on-hand delta and its immutable movement commit in one transaction.
- A physical adjustment sets an exact observed on-hand quantity and cannot reduce it below active reservations.
- A no-op adjustment creates neither movement nor audit event.
- Movement identity, type, delta, variant, warehouse and occurrence time are immutable.
- Balance version advances for every on-hand or reserved mutation and produces a new strong ETag.

## Reservation

- Lines are de-duplicated by variant and positive quantities are summed with overflow protection.
- A command contains at least one and at most 100 distinct lines.
- Multi-line reservation is all-or-nothing.
- Rows are locked in stable warehouse/variant order before availability is checked.
- `ACTIVE` is the only mutable state; terminal states are `COMMITTED`, `RELEASED`, `EXPIRED`.
- Commit decreases on-hand and reserved by the same quantity and appends physical movements.
- Release and expiry decrease reserved only.
- Commit/release/expire replay is idempotent; a conflicting terminal transition fails closed.
- TTL is 15 minutes by default. Exactly one strict extension is allowed before expiry.
- An expired reservation cannot be extended or committed even when the expiry job has not processed it yet.

## Module and API boundaries

- Inventory references catalog variant UUIDs but never mutates catalog aggregates or reads catalog tables directly.
- Receipt and new reservation require an active variant through the catalog integration contract.
- Existing archived stock may still be reconciled or released safely.
- Public API exposes only `IN_STOCK` and `OUT_OF_STOCK`; exact quantities, movement reasons and reservation identities are never public.
- Unknown and missing public variants are indistinguishable from out-of-stock variants.
- Browser reservation mutations are out of scope; ordering will call the named inventory application contract.

## Idempotency, security and audit

- Critical commands bind caller/owner scope, operation, idempotency key and canonical request fingerprint.
- Same key and fingerprint replay the original result without another balance, movement, reservation event or audit change.
- Same key with another fingerprint returns conflict.
- Warehouse endpoints require verified email, MFA, CSRF and `WAREHOUSE_MANAGER` or `ADMIN`.
- Reconciliation requires current strong `If-Match`; missing is `428`, stale is `412`.
- Successful warehouse mutations append bounded safe inventory audit in the same transaction.
- Audit excludes sessions, tokens, customer data and arbitrary request bodies.

## Expiry job

- One database lease identifies the active worker; local memory is never coordination state.
- Lease acquisition/takeover is atomic and uses database time.
- Each run processes a bounded batch with `FOR UPDATE SKIP LOCKED`.
- Expiry transition and reserved decrement are one transaction.
- Crash before commit has no effect; crash after commit is safely observable as terminal state.
