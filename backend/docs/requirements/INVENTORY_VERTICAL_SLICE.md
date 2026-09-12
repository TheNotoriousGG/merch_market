# Inventory vertical slice — техническое задание

Статус: утверждено как scope этапа 8.

Дата фиксации: 19 августа 2026 года.

## 1. Цель

Реализовать production-oriented inventory-модуль «Амра Шоп», который добавляет достоверную доступность к catalog variants, поддерживает физические складские движения и предоставляет будущему ordering-модулю атомарные резервы без oversell.

Срез должен оставаться корректным при конкурентных запросах и нескольких stateless backend instances. PostgreSQL является единственным source of truth.

## 2. Scope

В этап входят:

- один активный склад `PRIMARY` с расширяемой multi-warehouse схемой;
- exact balance по catalog variant: on-hand, reserved, available и optimistic version;
- immutable physical movement ledger;
- all-or-nothing multi-line reservations;
- commit, release, expiry и одно strict extension;
- public batch availability без точных остатков;
- warehouse receipt, balance read и physical reconciliation API;
- durable idempotency, inventory audit и RFC 9457 errors;
- bounded expiry worker с PostgreSQL lease;
- Flyway migrations, indexes, grants и reconciliation/query-plan tests;
- generated Java transport и TypeScript Fetch client из OpenAPI.

## 3. За пределами этапа

- cart, checkout, order state machine и payment orchestration;
- браузерный endpoint создания reservation;
- pricing, promotions и денежные поля;
- preorder, backorder, negative stock и partial fulfillment;
- transfer между складами и multi-warehouse routing UI;
- supplier, purchase order, lot/batch/serial tracking;
- Redis/cache, message broker и отдельный inventory service;
- production scheduler platform и distributed tracing vendor.

## 4. Термины и quantity model

- `onHand` — физически учтённое количество единиц SKU на складе.
- `reserved` — количество в активных резервах.
- `available` — вычисляемое `onHand - reserved`.
- `movement` — неизменяемое физическое изменение on-hand.
- `reservation event` — неизменяемая запись изменения lifecycle/expiry без подмены physical movement.

Количество является целым `long`/`bigint`. Дробные единицы не поддерживаются. Все публичные и внутренние операции проверяют overflow. Инвариант: `onHand >= 0`, `reserved >= 0`, `reserved <= onHand`.

## 5. Warehouse и catalog boundary

Migration создаёт warehouse `PRIMARY` со стабильным UUID. Все inventory rows содержат `warehouse_id`, хотя HTTP MVP не предлагает выбор склада.

Inventory хранит immutable `variant_id`, но не создаёт JPA relation и foreign key к catalog tables. Новый receipt и reservation проверяют существование и active status через named catalog application contract. Catalog variants не удаляются физически; archive не ломает существующие balances/reservations. Archived balance разрешено reconciliate к фактическому значению и освобождать/commit уже созданный reserve по ordering policy, но новый receipt/reservation для archived variant запрещён.

## 6. Balance и immutable movement ledger

Balance key — `(warehouse_id, variant_id)`. Поля: on-hand, reserved, version, created/updated timestamps.

Receipt:

- принимает положительную quantity;
- атомарно увеличивает on-hand;
- повышает balance version;
- добавляет один `RECEIPT` movement;
- повтор той же idempotent command не создаёт второй эффект.

Physical reconciliation:

- принимает exact observed on-hand, reason и optional bounded reference;
- требует current balance ETag;
- вычисляет signed delta и movement type;
- запрещает target ниже reserved;
- target, равный current on-hand, отклоняется как `INVENTORY_NO_CHANGE` без side effects;
- stale write возвращает `412 STALE_RESOURCE_VERSION`.

Movement delta не равен нулю. Movement никогда не обновляется и не удаляется runtime role. Balance и movement commit/rollback вместе.

## 7. Reservation aggregate

Reservation содержит UUIDv7, owner reference без PII, status, expiresAt, extension count, version и 1–100 distinct lines. Line содержит warehouse, variant и positive quantity.

Input duplicates агрегируются в first-seen order с checked addition. Команда отклоняется при overflow или более чем 100 distinct variants.

Lifecycle:

```text
ACTIVE ── commit ──> COMMITTED
   ├──── release ──> RELEASED
   └──── expire ───> EXPIRED
```

Terminal reservation никогда не возвращается в `ACTIVE`. Partial commit/release отсутствуют.

Create reservation:

1. Проверить active catalog variants одним batch contract.
2. Получить/create balance rows и заблокировать их в stable order.
3. Проверить `available >= requested` для каждой line.
4. Увеличить reserved по всем lines.
5. Создать reservation, lines и `CREATED` event.
6. Commit одной транзакцией или rollback полностью.

Default expiry — `Clock.now() + 15m`. Configuration bounded и задаётся application property.

Commit разрешён только для фактически неистёкшего `ACTIVE` reserve. Он уменьшает on-hand и reserved на line quantity и добавляет `RESERVATION_COMMIT` movement для каждой line.

Release уменьшает reserved и создаёт terminal event без physical movement. Expiry семантически равен release, но получает status/reason `EXPIRED`.

## 8. Strict extension

- extension доступен только `ACTIVE` reservation до expiresAt;
- extension count изначально 0 и может стать только 1;
- новый expiresAt строго больше старого и равен старому expiresAt плюс configured TTL;
- второй вызов с тем же idempotency key replay-ит результат;
- другой extension после первого возвращает `RESERVATION_EXTENSION_LIMIT_REACHED`;
- extension не меняет balance quantities.

## 9. Concurrency и transaction policy

- isolation — PostgreSQL `READ COMMITTED`;
- balance rows блокируются `FOR UPDATE` в deterministic `(warehouse_id, variant_id)` order;
- reservation row блокируется перед terminal transition;
- constraints являются последним barrier для negative/oversold state;
- deadlock/lock timeout не маскируется retry loop внутри HTTP request;
- idempotency проверяется внутри transaction до side effects;
- concurrent requests на последнюю единицу дают ровно один success;
- multi-line command не оставляет частичный reserve.

## 10. Expiry worker

Worker запускается bounded schedule, но coordination находится в PostgreSQL:

- lease имеет stable job name, owner instance id и `leased_until`;
- acquire/renew/takeover использует database time и atomic compare;
- один run выбирает максимум configured batch size;
- candidates: `ACTIVE` и `expires_at <= database clock`;
- rows выбираются `FOR UPDATE SKIP LOCKED`;
- каждый batch transaction освобождает reserved и фиксирует `EXPIRED` events;
- повторный run не меняет terminal reservations;
- graceful shutdown не продлевает lease бесконечно.

## 11. Public HTTP contract

`GET /api/v1/inventory/availability?variantId=...` принимает 1–60 repeated UUID, de-duplicates first-seen order и возвращает:

- `IN_STOCK`, если exact available > 0;
- `OUT_OF_STOCK` для zero/missing/unknown variant.

Response содержит `asOf` и `X-Trace-Id`. Exact quantity, warehouse internals, reservation IDs и movement data не публикуются. Endpoint anonymous и batch-oriented, чтобы product page/list не создавали N+1.

## 12. Warehouse HTTP contract

Endpoints stage 8:

- `GET /api/v1/admin/inventory/balances/{variantId}`;
- `POST /api/v1/admin/inventory/balances/{variantId}/receipts`;
- `POST /api/v1/admin/inventory/balances/{variantId}/adjustments`.

Balance read возвращает exact quantities и strong ETag. Receipt требует Idempotency-Key и CSRF. Reconciliation дополнительно требует If-Match. Mutation response содержит updated balance и immutable movement.

Warehouse endpoints требуют verified email, allowed MFA ACR и роль `WAREHOUSE_MANAGER` или `ADMIN`. Frontend localizes stable error codes.

## 13. Internal application contract

Named inventory interface для будущего ordering:

- batch exact availability/check;
- create reservation;
- read reservation outcome;
- strict extension;
- commit;
- release.

Contract принимает opaque owner/reference UUID и не принимает customer PII, price или address. Ordering не получает repository/table access.

## 14. Errors

Минимальные stable codes:

- `INVENTORY_BALANCE_NOT_FOUND` — admin balance отсутствует;
- `INVENTORY_VARIANT_NOT_ACTIVE` — receipt/reserve с неизвестным или inactive variant;
- `INSUFFICIENT_STOCK` — хотя бы одна line не может быть зарезервирована;
- `INVENTORY_NO_CHANGE` — reconciliation не меняет on-hand;
- `INVENTORY_IDEMPOTENCY_CONFLICT` — key reused with another fingerprint;
- `RESERVATION_NOT_FOUND`;
- `RESERVATION_EXPIRED`;
- `RESERVATION_STATE_CONFLICT`;
- `RESERVATION_EXTENSION_LIMIT_REACHED`;
- `PRECONDITION_REQUIRED` и `STALE_RESOURCE_VERSION`.

Все HTTP failures используют RFC 9457 `application/problem+json`, safe detail, instance, traceId и violations без SQL/internal state.

## 15. Idempotency и audit

Warehouse и reservation critical commands используют durable scoped idempotency. Fingerprint строится из canonical validated command, а не raw JSON. Replay возвращает исходный result. Conflict ничего не меняет.

Успешный warehouse mutation пишет inventory audit в той же transaction: actor, time, warehouse, variant, action, reason, reference, before/after quantities/version и correlation ID. Safe diff bounded; arbitrary body, credentials, tokens и PII запрещены. Failed/no-op/replay не создают audit.

Reservation events содержат domain state, но не заменяют administrative audit.

## 16. Persistence design constraints

Планируемые inventory-owned tables:

- `inventory_warehouses`;
- `inventory_balances`;
- `inventory_movements`;
- `inventory_reservations`;
- `inventory_reservation_lines`;
- `inventory_reservation_events`;
- `inventory_command_idempotency`;
- `inventory_job_leases`;
- `inventory_audit_events`.

Имена constraints/indexes explicit. Runtime получает только необходимый SELECT/INSERT/UPDATE; DELETE для ledger/audit отсутствует. Critical indexes проектируются для variant balance read, active expiry scan, owner/idempotency lookup и movement reconciliation.

## 17. Testing and acceptance

Обязательные tests:

- unit/property invariants для quantities, checked aggregation и lifecycle;
- migration from scratch, grants, constraints и append-only barriers;
- atomic balance/movement commit and rollback;
- two reservations race for last unit — one success;
- multi-line rollback when one line is insufficient;
- deterministic lock order under reversed inputs;
- duplicate receipt/reserve/commit/release/extend;
- expiry versus commit race;
- lease exclusivity, expired takeover and crash-safe retry;
- reconciliation: on-hand equals physical movement sum from initial zero; reserved equals active line sum;
- public batch query remains bounded and hides exact values;
- warehouse authorization, MFA, CSRF, ETag and audit correlation;
- generated Java/TypeScript drift and OpenAPI compatibility;
- representative query plans under 2-second runtime timeout;
- final non-root container smoke with migrations and availability endpoint.

## 18. Блоки реализации

1. ТЗ, ADR, OpenAPI и module boundaries.
2. Balance/movement domain.
3. Reservation lifecycle domain.
4. Flyway schema, constraints, grants and migration tests.
5. Atomic balance/movement persistence and reconciliation.
6. All-or-nothing reservation application contract and concurrency.
7. Public batch availability API.
8. Warehouse admin API.
9. Commit/release/strict extension contracts.
10. Expiry worker with PostgreSQL lease.
11. Audit/security/race/query-plan acceptance.
12. Generated client/container/reproducibility/docs closeout.

## 19. Definition of Done

- `feature/inventory` содержит logical Conventional Commits и fast-forward merge-ready history;
- every approved invariant protected by domain/application/database layer as appropriate;
- OpenAPI, generated artifacts, Javadoc, architecture and quality gates green;
- 100% critical inventory branch/invariant scenarios asserted, overall thresholds not reduced;
- container smoke proves Flyway, health and public availability;
- Markdown context and deferred gates current;
- no cache, broker, PII, secret, generated domain/JPA model or unrelated feature introduced.
