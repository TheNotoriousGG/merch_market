# ADR-0007: Inventory consistency boundaries

- Status: Accepted
- Date: 2026-08-19

## Context

Catalog now owns stable product and variant identities but intentionally has no stock semantics. Cart and ordering need a strongly consistent inventory contract that prevents oversell on several stateless application instances. Warehouse employees also need traceable receipt and reconciliation commands. Redis, a broker, distributed locks and a separate inventory service would add failure modes without solving a measured scaling problem.

## Decision

- Inventory remains a module of the modular monolith and owns balances, physical movements, reservations, reservation events, command idempotency, job leases and inventory audit tables.
- PostgreSQL is the source of truth. A balance stores non-negative `on_hand` and `reserved`; `available = on_hand - reserved` and `0 <= reserved <= on_hand` are protected in domain code, atomic SQL and database constraints.
- The MVP has one seeded `PRIMARY` warehouse, while every balance, movement and reservation line carries `warehouse_id` so additional warehouses do not require a destructive redesign.
- Inventory references immutable catalog variant UUIDs but has no cross-module JPA relation or database foreign key to catalog-owned tables. Creation and reservation validate variants through a named catalog application contract. Catalog variants are archived rather than deleted.
- Public HTTP exposes batch `IN_STOCK`/`OUT_OF_STOCK` only. Missing balances and unknown identifiers fail closed as `OUT_OF_STOCK`; exact quantities are warehouse/internal data.
- Physical on-hand changes append an immutable movement in the same transaction as the balance update. Reservation-only changes do not create physical movements; they append immutable reservation events. Ledger reconciliation is an acceptance gate.
- Multi-line reservation is all-or-nothing. Balance rows are locked in deterministic warehouse/variant order under `READ COMMITTED`; availability is checked after locking and every line succeeds or the transaction rolls back.
- Reservation lifecycle is `ACTIVE → COMMITTED | RELEASED | EXPIRED`. Terminal states never reopen. Commit decrements both on-hand and reserved and appends one physical movement per line. Release/expiry decrements reserved only.
- Default TTL is 15 minutes. One active reservation may be extended exactly once and only to a strictly later expiry, by one configured TTL. Expired reservations cannot be extended or committed.
- Expiry processing uses a database lease, bounded `FOR UPDATE SKIP LOCKED` batches and idempotent terminal transitions. Another instance can safely take over an expired lease.
- Warehouse receipt is a commutative delta command protected by durable idempotency. Physical reconciliation sets an exact on-hand value, requires strong `If-Match`, and cannot set on-hand below reserved.
- Warehouse HTTP commands require verified email, `WAREHOUSE_MANAGER` or `ADMIN`, MFA, CSRF and an append-only inventory audit event correlated by `X-Trace-Id`.
- No cache, broker, distributed lock, preorder, negative stock or partial reservation/commit is introduced in this stage.

## Consequences

- Concurrent reservation correctness depends only on PostgreSQL transactions and constraints, so horizontal application scaling does not change stock semantics.
- Catalog and inventory can evolve independently while keeping one explicit variant-reference contract.
- Public clients cannot infer warehouse quantities or distinguish an unknown variant from unavailable stock.
- Current balance gives bounded reads; immutable movements and reservation events provide reconstruction and reconciliation evidence.
- A hot SKU serializes briefly on one balance row. This is accepted for the initial 50 RPS target and must be measured before considering another architecture.
- Ordering can use an in-process application contract later without exposing customer reservation mutation endpoints to browsers.

## Alternatives considered

- Derive every balance from the movement ledger: rejected because availability is a hot correctness path and must remain a bounded row read/lock.
- Redis counters or distributed locks: rejected because dual-write and lease failure modes weaken the PostgreSQL source of truth.
- Serializable transactions for every command: rejected because deterministic row locks and constraints address the approved races with less abort pressure.
- Database foreign keys into catalog tables: rejected because they create table-level module coupling; immutable non-deleted variant identities plus a named application contract provide the required integrity boundary.
- Public exact stock: rejected because the storefront needs reservability, not warehouse disclosure.
