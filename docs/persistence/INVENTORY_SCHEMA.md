# Inventory persistence model

Status: Flyway V7 baseline accepted for stage 8; V8 hardens UUIDv7 checks against PostgreSQL `UNKNOWN` semantics; V9/V10 add immutable exact command-result snapshots.

## Ownership boundary

Inventory owns eleven relational tables in `amra_shop`. Catalog variant UUIDs are external references, not database foreign keys: the inventory application layer validates active variants through `CatalogVariantInventoryView`. This prevents a hidden cross-module persistence dependency while keeping UUIDv7 validation at the inventory boundary.

UUIDv7 constraints use `(uuid_extract_version(value) = 7) IS TRUE`. The explicit truth test is required because a UUID without an encoded RFC version yields `NULL`, and a plain SQL `CHECK (... = 7)` would otherwise accept the `UNKNOWN` result.

`inventory_warehouses` contains one migration-seeded `PRIMARY` warehouse. Warehouse identity remains present in every balance, movement and reservation line so adding warehouses does not require reshaping ledger history.

## State and ledgers

- `inventory_balances` is the mutable exact snapshot keyed by `(warehouse_id, variant_id)` and enforces `0 <= reserved <= on_hand` plus non-negative optimistic version.
- `inventory_movements` is the append-only physical on-hand ledger. Type determines the mandatory delta sign; only reservation commits may carry `reservation_id`.
- `inventory_reservations` is the mutable lifecycle root. Status, `terminal_at`, expiry, extension count and version are constrained.
- `inventory_reservation_lines` is immutable after insert and references an existing inventory balance.
- `inventory_reservation_events` is append-only lifecycle evidence with database-validated transition shape.
- `inventory_audit_events` is append-only administrative evidence with bounded safe JSON object diff.
- `inventory_stock_command_results` is an append-only typed snapshot of the exact balance representation produced by a physical movement. It enables exact idempotent replay even after later balance mutations without putting stock state in an opaque JSON payload.
- `inventory_reservation_command_results` is an append-only typed reservation snapshot keyed by lifecycle event. V10 derives historical version/extension values for existing events and rewires existing create-idempotency results during upgrade.

Runtime may update balances, reservations, idempotency records and leases, but cannot delete them. Runtime may only insert movements, reservation lines/events, typed command results and audit events. Migration-owned triggers reject ledger/result/audit update or delete even when a more privileged role is used accidentally.

## Coordination records

- `inventory_command_idempotency` binds actor scope and key to operation, canonical SHA-256 fingerprint and completed resource.
- Warehouse command fingerprints use length-prefixed nullable components before SHA-256, so separators and the literal string `null` cannot produce ambiguous commands.
- `inventory_job_leases` stores the stable job name, instance owner, database deadline and optimistic version; it is the only cross-instance expiry-worker coordination state.

## Critical access paths

- Public/admin variant reads use `ix_inventory_balances__variant_warehouse`.
- Balance reconciliation uses `ix_inventory_movements__balance_time`.
- Exact command replay uses the movement primary key and `ix_inventory_stock_command_results__balance` supports balance/version diagnostics.
- Reservation replay uses the event primary key; `ix_inventory_reservation_command_results__reservation_version` supports lifecycle diagnostics.
- Expiry scans use the partial `ix_inventory_reservations__active_expiry` index.
- Reserved reconciliation and deterministic balance locking use `ix_inventory_reservation_lines__balance_reservation`.
- Reservation history uses owner and reservation-event time indexes.

Query-plan acceptance on representative data remains block 11; V7 only establishes indexes required by already-approved query shapes.
