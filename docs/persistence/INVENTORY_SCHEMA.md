# Inventory persistence model

Status: Flyway V7 baseline accepted for stage 8.

## Ownership boundary

Inventory owns nine relational tables in `amra_shop`. Catalog variant UUIDs are external references, not database foreign keys: the inventory application layer validates active variants through `CatalogVariantInventoryView`. This prevents a hidden cross-module persistence dependency while keeping UUIDv7 validation at the inventory boundary.

`inventory_warehouses` contains one migration-seeded `PRIMARY` warehouse. Warehouse identity remains present in every balance, movement and reservation line so adding warehouses does not require reshaping ledger history.

## State and ledgers

- `inventory_balances` is the mutable exact snapshot keyed by `(warehouse_id, variant_id)` and enforces `0 <= reserved <= on_hand` plus non-negative optimistic version.
- `inventory_movements` is the append-only physical on-hand ledger. Type determines the mandatory delta sign; only reservation commits may carry `reservation_id`.
- `inventory_reservations` is the mutable lifecycle root. Status, `terminal_at`, expiry, extension count and version are constrained.
- `inventory_reservation_lines` is immutable after insert and references an existing inventory balance.
- `inventory_reservation_events` is append-only lifecycle evidence with database-validated transition shape.
- `inventory_audit_events` is append-only administrative evidence with bounded safe JSON object diff.

Runtime may update balances, reservations, idempotency records and leases, but cannot delete them. Runtime may only insert movements, reservation lines/events and audit events. Migration-owned triggers reject ledger/audit update or delete even when a more privileged role is used accidentally.

## Coordination records

- `inventory_command_idempotency` binds actor scope and key to operation, canonical SHA-256 fingerprint and completed resource.
- `inventory_job_leases` stores the stable job name, instance owner, database deadline and optimistic version; it is the only cross-instance expiry-worker coordination state.

## Critical access paths

- Public/admin variant reads use `ix_inventory_balances__variant_warehouse`.
- Balance reconciliation uses `ix_inventory_movements__balance_time`.
- Expiry scans use the partial `ix_inventory_reservations__active_expiry` index.
- Reserved reconciliation and deterministic balance locking use `ix_inventory_reservation_lines__balance_reservation`.
- Reservation history uses owner and reservation-event time indexes.

Query-plan acceptance on representative data remains block 11; V7 only establishes indexes required by already-approved query shapes.
