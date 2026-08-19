# Реестр архитектурных решений

Статус: базовые решения для инициализации и MVP приняты.

Обновлено: 19 августа 2026 года.

Документ является источником истины для устойчивых решений. Детали реализации уточняются ADR в feature-ветках, но не должны молча противоречить этому реестру.

## P0 — разрешение на инициализацию

| ID | Решение | Статус | Зафиксированный вариант |
| --- | --- | --- | --- |
| P0-01 | Runtime | Принято | Java 25; Spring Boot 4.1.0; Gradle Wrapper 9.6.1. Версии проверены по официальной документации 19.08.2026. Коммерческая Tanzu-поддержка не требуется. |
| P0-02 | Web stack | Принято | Spring MVC на virtual threads; без WebFlux/R2DBC. DB и external concurrency остаётся bounded. |
| P0-03 | Database | Принято | Managed PostgreSQL в production; containers локально/test; schema `amra_shop`; отдельные owner/migration/runtime roles. |
| P0-04 | MVP | Принято | Catalog, categories, SKU, inventory, customer/identity, favorites, cart, pricing, orders и admin API; payment/delivery/notification через fake adapters. |
| P0-05 | Identity | Принято | Отдельный HA Keycloak со своей DB; OIDC; backend-managed HttpOnly session; RBAC; MFA для admin API. |
| P0-06 | OpenAPI generation | Принято | Генерировать API interfaces, transport/error DTO и TypeScript client. Domain/JPA models — вручную; mapping — MapStruct. |
| P0-07 | API versioning | Принято | Major в path (`/api/v1`), Spring MVC versioning, deprecation/sunset; breaking change требует нового major. |
| P0-08 | Money | Принято | `BigDecimal` + ISO currency; MVP — RUB; tax-inclusive; `HALF_UP`, 2 знака, line-level rounding и deterministic residual allocation. |
| P0-09 | Product/stock | Принято | Product → Variant/SKU → Inventory; UUIDv7 + immutable business SKU; один склад с расширяемой моделью; negative stock/preorder запрещены. |
| P0-10 | Git | Принято | Initial commit в `main`, затем short-lived feature branches, Conventional Commits, rebase + fast-forward, protected main, SemVer. |

## Архитектура

- Модульный монолит: Spring Modulith + ArchUnit; hexagonal boundaries внутри значимых модулей.
- Один Gradle application module. Внутри модуля пакеты `api / application / domain / infrastructure`.
- Начальные модули: `catalog`, `inventory`, `customer`, `identity-access`, `cart`, `pricing`, `ordering`, `administration`, `outbox`.
- Domain не зависит от Spring, JPA и generated code.
- Модуль владеет таблицами; прямой доступ к чужим repositories и межмодульная JPA-навигация запрещены.
- Общение — published application contracts/domain events; cycles запрещены.
- Cross-module ACID допустим только для критического use case через public contracts. Post-commit effects — transactional outbox.
- Один магазин; мультитенантность и `tenant_id` не закладываются.

## API

- OpenAPI — source of truth; lint, validation, generated-diff и breaking-change comparison с `main` обязательны.
- OpenAPI 3.0.3 выбран до стабилизации полного toolchain для 3.1; generation и compatibility gates описаны ADR-0003.
- Swagger UI: local/test; в production — protected admin access.
- Errors: RFC 9457 Problem Details, stable business code, `traceId`, validation violations.
- Пользовательские тексты локализует frontend; backend возвращает codes/parameters.
- Time: ISO 8601 UTC, Java `Instant`, injected `Clock`.
- Nullability: OpenAPI + JSpecify. `Optional` допустим как return type, но не в DTO/entities/parameters.
- Critical commands используют `Idempotency-Key`, request fingerprint, scoped TTL и conflict при другом payload.
- Catalog/admin: page/size + totals; audit/events/large mutable feeds: cursor/keyset.
- Filtering/sorting — explicit typed allowlists без generic query language.
- Concurrent admin edit: optimistic locking + ETag/`If-Match`; stale update — 412.
- Frontend client содержит версию spec и предназначен для публикации как versioned npm package. Пока действует ADR-0002, backend выпускает проверяемый reproducible source ZIP; Node build/pack и публикация включаются вместе с GitLab Package Registry.

## Persistence и данные

- Spring Data JPA/Hibernate; OSIV off; bounded aggregates, explicit fetch/projections и N+1 tests. jOOQ — только после измерений.
- Flyway manual SQL migrations; Hibernate schema validation only.
- PostgreSQL foundation, three-role privilege model и граница infrastructure/Flyway bootstrap зафиксированы ADR-0004.
- PostgreSQL: plural `snake_case`, meaningful explicit constraint/index names, без reserved words.
- UTC + `timestamptz`; `created_at`, `updated_at`, `version`; admin actions — отдельный append-only audit trail.
- UUIDv7 для internal/external IDs; order дополнительно имеет читаемый непредсказуемый public number без PII.
- Нет universal soft delete: explicit statuses, retention, targeted deletion/anonymization.
- Expand/contract минимум через два релиза; backfill — separate resumable job.
- Index создаётся под constraint/query; critical plans проверяются `EXPLAIN (ANALYZE, BUFFERS)` на realistic data.
- SQL timeout по классу операции: ориентир 2 секунды для обычных queries, 10 секунд для admin reports, отдельные limits для jobs. PII не логируется.
- Production: continuous PITR, daily backup, restore drills, encryption и runbook.

## Каталог, цены и склад

- Product/technical requirements and acceptance criteria для этапа каталога зафиксированы в `docs/requirements/CATALOG_VERTICAL_SLICE.md`.
- Public/admin contract boundary, pagination, redirects, composition and concurrency semantics зафиксированы ADR-0006; компактный implementation checklist находится в `docs/catalog/CATALOG_INVARIANTS.md`.
- Category: adjacency list (`parent_id`), recursive CTE, sibling-unique slug, order/status, cycle prevention.
- Product lifecycle: `DRAFT → ACTIVE → ARCHIVED`; completeness validation и audit перед publish.
- Editable slug с old aliases/redirects; без cycles/reuse.
- SKU immutable, normalized, case-insensitive, например `AMR-TS01-BLK-M`; старая variant архивируется.
- SKU-critical/filterable attributes реляционные и typed; limited validated JSONB только для noncritical metadata.
- Base price на SKU с effective periods/history; discounts separate; order содержит полный price/tax snapshot.
- Search: PostgreSQL FTS + `pg_trgm` за search port; только published products; OpenSearch — после измерений.
- Media: S3-compatible object storage + CDN; PostgreSQL metadata/order/alt/object key; presigned upload, async derivatives, immutable URLs.
- Inventory: current balance + immutable movement ledger; balance/movement atomic.
- Reservation: PostgreSQL source of truth, atomic, TTL 15 минут configurable, максимум одно строгое extension, idempotent release/expiry worker.
- Inventory stage 8 scope, public/admin/internal boundaries and PostgreSQL locking semantics are fixed by ADR-0007 and `docs/requirements/INVENTORY_VERTICAL_SLICE.md`; compact review guardrails live in `docs/inventory/INVENTORY_INVARIANTS.md`.
- Warehouse mutation idempotency stores an immutable typed result keyed by movement; replay returns the original balance/movement/ETag even after later stock changes, while the canonical fingerprint is actor-scoped and length-prefixed before SHA-256.
- Reservation command idempotency stores immutable typed results keyed by lifecycle event; create/extend/commit/release replay their original snapshot after later transitions, and V10 backfills pre-existing event history without JSON state.
- Cache не внедряется заранее; Redis возможен через adapter после измерения и определения invalidation.

## Customer, favorites и cart

- Full guest checkout с последующей безопасной привязкой заказа к account.
- Минимальный profile: display name, verified email, optional phone, saved addresses.
- Verified email обязателен для кабинета и guest-order linking; изменение email требует повторной verification.
- Guest order access: scoped expiring emailed token; в DB только hash; rotation/revocation.
- Saved addresses + immutable typed address snapshot в order.
- Guest/authenticated cart — PostgreSQL; secure guest cookie, deterministic merge, optimistic locking.
- Cart retention: guest 30 дней, authenticated 90 дней inactivity; cleanup bounded batches.
- Guest favorites: anonymous profile, 30 дней, merge after login.
- Cart price не фиксируется; backend revalidates и frontend показывает изменения. Snapshot создаётся при confirmation.
- Spring Session JDBC в PostgreSQL. Customer: 30 минут inactivity/30 дней absolute; admin: 15 минут/8 часов.
- Logout, blocking и critical permission changes revoke sessions.

## Ordering и integrations

- State machine: `DRAFT → PENDING_CONFIRMATION → CONFIRMED → PROCESSING → SHIPPED → DELIVERED`; также `CANCELLED`, `RETURN_REQUESTED`, `RETURNED`, `REFUNDED`. Payment/delivery statuses separate.
- Checkout: revalidate → atomic reserve → pending → payment → confirmed; failure/expiry освобождает reserve idempotently.
- После `CONFIRMED` composition, price, discounts и address snapshots immutable.
- Self-cancel до `PROCESSING`; после — manager request.
- Return целого заказа/lines с reason, quantity, evidence и status history; refund status отдельный.
- MVP: одна shipment; data model допускает multi-shipment позднее.
- Payment/delivery/notification ports используют controllable fake adapters. Notifications — async via outbox.
- Webhook: signature, timestamp, replay protection, persist-before-processing, deduplication и idempotency.
- Promotions: bounded typed model без generic rule engine; default non-stacking, best offer, explainable snapshot.

## Security и эксплуатация

- Browser auth: Secure HttpOnly SameSite cookie, CSRF, CORS allowlist, rotation; OIDC token скрыт от JS.
- Roles из permissions: `CUSTOMER`, `CATALOG_MANAGER`, `ORDER_MANAGER`, `WAREHOUSE_MANAGER`, `SUPPORT`, `ADMIN`.
- `/api/v1/admin/**`: separate DTO/permissions/rate limits/audit; MFA claim обязателен; direct DB access запрещён.
- Keycloak — отдельный infrastructure component, минимум два production instances, own DB, backup и health checks.
- Identity foundation использует Keycloak 26.7.0, Spring Security OIDC Authorization Code + PKCE и Spring Session JDBC; детали и upgrade boundary зафиксированы ADR-0005.
- Backend principal и ключ отзыва сессий — immutable OIDC `sub`; неизвестные realm roles отбрасываются allowlist mapping.
- Admin MFA доказывается одновременно `ROLE_ADMIN` и разрешённым `acr`; начальный уровень — `2`, выдаваемый только MFA flow Keycloak.
- Secrets — external Secret Manager; provider выбирается вместе с production platform.
- TLS обязателен для external/inter-service/PostgreSQL traffic; disks/backups/object storage encrypted.
- PII classified; retention/anonymization; нет PII в logs/metrics/traces/URLs/idempotency payloads.
- Rate limits на ingress и business layer; 429 + `Retry-After`.
- External calls: timeout, limited jittered retries только для safe/idempotent operations, circuit breaker/bulkhead при необходимости.
- Virtual threads для blocking I/O, но Hikari/external/CPU/job concurrency bounded; unbounded parallelism запрещён.
- Jobs: PostgreSQL lease/lock, idempotent bounded batches, safe takeover by another instance.
- Liveness проверяет process; readiness — critical dependencies включая PostgreSQL. Noncritical adapters не выключают backend.
- Graceful shutdown: stop traffic, drain requests/jobs within timeout.
- OpenTelemetry + Micrometer + structured JSON logs; vendor-neutral telemetry/correlation IDs.
- Target: 99.9% availability, RPO ≤5 минут, RTO ≤30 минут. Initial sizing: до 50 RPS peak, 10 000 daily visitors.

## Quality и delivery

- Tests: JUnit 6, AssertJ, Mockito, ArchUnit, Testcontainers, WireMock; jqwik для money/state/inventory properties; mocks только на boundaries.
- Coverage: ≥80% line, ≥70% branch overall; 100% critical domain invariants; mutation tests critical modules.
- Static analysis: Spotless, Checkstyle, Error Prone/NullAway, SonarQube после Java 25 compatibility check.
- Javadoc: public contracts, invariants, concurrency/transaction semantics; doclint в CI.
- Lombok whitelist: `@Slf4j`, `@RequiredArgsConstructor`, targeted `@Getter`, `@Builder`; `@Data` и entity-generated identity methods запрещены.
- GitLab CI/Registry; protected `main`; merge requests; immutable promoted image. Активация remote/runner временно отложена по ADR-0002, пока repository остаётся local-only без release/deployment.
- Custom multi-stage Dockerfile + non-root distroless Java 25, pinned digests, SBOM/scanning.
- Environments: local, test, staging, production. Expand migration до app deploy; contract migration после удаления old code.
- SemVer, Conventional Commits, logical reversible commits.

## Отложено или исключено из MVP

| Область | Статус и условие возврата |
| --- | --- |
| Production platform/orchestrator | Выбрать до production; backend остаётся portable/stateless. |
| Secret Manager provider | Выбрать вместе с production platform. |
| Real payment/delivery/notification providers | Подключать отдельными adapters после product choice. |
| Redis/cache | Только после измерения bottleneck и определения invalidation. |
| Message broker | При появлении реального independent consumer/throughput need. |
| Feature flags | Не нужны сейчас; вернуться для gradual rollout/A-B scenarios. |
| Formal STRIDE process | Не вводится; security review и CI checks сохраняются. |
| Legal-document/consent versioning | Не входит в MVP; юридические требования закрыть до production. |
| Provider reconciliation job | Проектировать вместе с real provider. |
| Preorder/negative stock | Preorder — отдельная будущая feature; negative stock запрещён. |
| Multi-shipment | Отложено; MVP — одна shipment. |
| Multi-tenancy | Не применяется; отдельное решение при нескольких storefronts. |
| GitLab remote/runner activation | Отложено владельцем 19.08.2026; обязательно до первого shared remote, release или deployment, см. ADR-0002. |

## Правило изменения

Изменение принятого решения требует причины, alternatives, consequences, migration/rollback plan и ADR либо обновления реестра в той же feature-ветке. Молчаливое отклонение в code запрещено.
