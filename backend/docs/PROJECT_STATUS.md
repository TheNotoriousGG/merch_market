# Текущее состояние backend

Обновлено: 13 сентября 2026 года.

## Короткий ответ

| Область | Состояние |
| --- | --- |
| Backend stages 0–10 | Завершены локально и находятся в `main` монорепозитория |
| Реализованные product slices | Catalog, Inventory, Customer/Favorites/Cart, Pricing и базовый Ordering |
| Последний закрытый acceptance | Этап 10; Ordering проходит промежуточные backend/frontend gates |
| Текущий разрешённый этап | 11. Ordering и checkout — в работе |
| Frontend/backend integration | Catalog, banners, profile, addresses, verified email, favorites и cart используют backend/generated client |
| Production-only решения | platform, Secret Manager, real providers и GitLab activation отложены |

## Завершено

- Архитектурный опрос и 120 решений.
- Утверждение implementation plan владельцем проекта.
- System context и ADR-0001 модульного монолита.
- Spring Boot 4.1.0 / Java 25 / Gradle 9.6.1 scaffold.
- Минимальные Spring MVC, validation и actuator dependencies.
- Virtual threads включены.
- Reproducible archives, dependency locking и SHA-256 dependency verification metadata.
- Smoke test application context.
- Clean build и повторный offline clean build прошли успешно на Eclipse Temurin 25.0.4.
- Engineering quality foundation: Spotless, Checkstyle, Error Prone, NullAway, Javadoc doclint, SonarQube integration и JaCoCo thresholds.
- Test foundation: JUnit 6, AssertJ, Mockito, jqwik, ArchUnit, Testcontainers, WireMock, Spring Modulith verification и PIT.
- ADR и merge request templates, testing strategy и описание local quality gates.
- Контролируемые fault probes подтвердили отказ Spotless, Checkstyle, NullAway, Javadoc, unit tests, architecture rules и JaCoCo при нарушении.

## Текущая точка

Этапы 0–8 завершены в локальном workflow и fast-forward слиты в `main`. GitLab activation и package publication отложены по ADR-0002 без ослабления локальных gates; это исключение не разрешает release или deployment.

- `37f6c12 build: enforce formatting and static analysis`;
- `b0647fd test: establish architecture and mutation testing`;
- `d0791bf docs: document engineering quality gates`.

Этап 4 завершён и fast-forward слит в `main`:

- `b6355c0 build: establish OpenAPI contract toolchain`;
- `2d8a8db feat: expose contract-driven platform API`;
- модульная OpenAPI 3.0.3 specification для `/api/v1`;
- RFC 9457 Problem Details и reusable pagination, cursor, idempotency, ETag, rate-limit conventions;
- OpenAPI Generator 7.22.0 для Java API/DTO и TypeScript Fetch client;
- OpenAPI Diff 2.1.7 для breaking-change comparison с `main`;
- contract-driven API discovery endpoint и выключенная по умолчанию documentation endpoint;
- ADR-0003 фиксирует выбор toolchain и границы generated code;
- полный local `clean check` проходит offline; configuration cache повторно используется;
- контролируемое удаление `GET /api/v1/` отклонено OpenAPI Diff как breaking change.

Этап 5 реализован в `feature/postgresql-foundation` и находится в `main`:

- `ed66702 build: establish PostgreSQL persistence runtime`;
- `74319d0 test: verify PostgreSQL migration boundaries`;
- PostgreSQL 18.4 Compose/Testcontainers image закреплён digest;
- `amra_owner`, `amra_migrator` и `amra_runtime` разделены;
- schema `amra_shop`, Flyway-only migrations, Hibernate validate и OSIV off;
- runtime DDL запрещён, Hikari pool и statement/lock timeouts ограничены;
- integration tests подтверждают fresh migration, idempotency, checksum failure, permissions и UUIDv7;
- ADR-0004, database conventions и recovery assumptions поддерживают межсессионный контекст;
- local Compose config, shell syntax и полный offline `qualityGate` проходят успешно.

Этап 6 реализован в `feature/identity-access` и находится в `main`:

- `46498f3 build: provision identity and session infrastructure`;
- `39f6d63 feat: enforce backend-managed browser security`;
- Keycloak 26.7.0 закреплён immutable multi-platform digest и использует отдельную PostgreSQL;
- local realm импортируется с client secret только из environment и содержит allowlist ролей без тестовых пользователей;
- OIDC Authorization Code + PKCE оставляет tokens в backend-managed JDBC session;
- `AMRA_SESSION` — Secure/HttpOnly/SameSite, unsafe requests защищены CSRF, credentialed CORS ограничен exact-origin allowlist;
- customer/admin inactivity и absolute lifetime реализованы раздельно;
- admin API и internal docs требуют `ROLE_ADMIN` вместе с MFA ACR `2`;
- verified email обязателен для protected customer API, а `GET /api/v1/session` возвращает только безопасные metadata;
- PostgreSQL-backed bulk revocation индексируется immutable OIDC `sub`;
- security integration tests доказывают denial, CSRF, CORS, MFA, role-escalation, expiry и revocation;
- реальный Keycloak container прошёл health check, realm OIDC discovery вернул ожидаемые issuer/endpoints;
- полный offline `clean qualityGate` прошёл; PIT mutation score — 93% при 97% line coverage mutated classes.

## Завершённый этап 7

Этап 7 реализован в `feature/catalog` и fast-forward слит в `main`:

- блок 1/12: OpenAPI contract и архитектурные границы реализованы, проходят validation и Java/TypeScript generation;
- блок 2/12: framework-free category domain реализован с immutable aggregate, typed values, hierarchy validation и unit/property tests;
- блок 3/12: product aggregate, typed attributes, immutable SKU variants, media metadata, lifecycle, publication report и slug/SKU namespaces реализованы;
- блок 4/12 завершён: Flyway V3 relational catalog schema, constraints, FTS/trigram/filter indexes и PostgreSQL integration tests готовы;
- JPA category adapter реализует application port, domain mapping, stable hierarchy snapshot и явную optimistic-version проверку со stale-write rejection;
- product persistence реализован как транзакционная композиция JPA aggregate root и ограниченного JDBC child-store для aliases, categories, typed attributes, variants, media и collections;
- product repository поддерживает canonical/alias lookup, полное восстановление aggregate, idempotent retry и optimistic-version rejection для stale/forged writes;
- блок 5/12 завершён: публичное дерево категорий строится через application query и один recursive CTE без N+1;
- anonymous `GET /api/v1/catalog/categories` реализует generated OpenAPI interface, возвращает только достижимые `ACTIVE` ветки, deterministic order, strong ETag и public cache policy;
- блок 6/12 завершён: anonymous product list поддерживает category descendants, active collection, 30-day `new`, same-variant size/color filters, FTS/trigram search и allowlisted sorting;
- page query возвращает exact totals и stable UUID tie-breaker максимум за три SQL-запроса независимо от числа products/variants; public media URL строится через отдельный port без раскрытия object key;
- блок 7/12 завершён: canonical public detail содержит visible references, characteristics, ordered media и только active variants; storage/status/version fields не покидают backend;
- historical slug возвращает прямой `301` на `/api/v1/catalog/products/{canonical}` только для active product, а draft/archive/missing неразличимы как `404`;
- блок 8/12 завершён: protected admin category API поддерживает создание, чтение и частичное изменение категорий через generated transport DTO;
- create всегда начинает с `HIDDEN`, использует PostgreSQL UUIDv7 и durable caller-scoped idempotency; повтор с тем же fingerprint возвращает тот же ресурс, а несовпадающая команда отклоняется;
- update требует strong `If-Match`, возвращает новый `ETag`, отклоняет stale write как `412` и проверяет полный hierarchy snapshot на cycle/orphan/depth/sibling-slug conflicts;
- явный `clearParent` устраняет неоднозначность между отсутствующим nullable полем и намеренным переносом категории в корень;
- `/api/v1/admin/catalog/**` требует `CATALOG_MANAGER` или `ADMIN`, verified email, MFA ACR и CSRF;
- блок 9/12 завершён: все generated admin operations для products, variants, media и collections реализованы вместо временных `501`;
- product root поддерживает draft creation, atomic partial revision, direct slug history, publish/archive transitions и strong root ETag;
- variant сохраняет immutable SKU, допускает presentation/attribute update и terminal archive; media сохраняет immutable storage identity и меняет только owner/presentation metadata;
- публикация получает active categories set-based запросом и повторно проверяет aggregate completeness; active product нельзя оставить без active variant или primary media;
- `AttributeValue` хранит stable `valueCode`, локализованный `label` и optional `colorHex` раздельно; Flyway V5 сохраняет эти данные без JSON и без потери round trip;
- editorial collection реализована отдельным aggregate с ordered membership, optimistic versioning и caller-scoped idempotency;
- изменение membership через product или collection command симметрично повышает версии затронутых representations, поэтому ETag не остаётся ложноположительно свежим;
- nullable partial arrays и explicit `clearVariant` сохраняют различие между omitted, empty и intentional null в generated DTO;
- блок 10/12 завершён: каждая успешная административная мутация каталога записывает append-only audit event в той же транзакции;
- Flyway V6 хранит actor, time, entity/action, reason, correlation ID и ограниченный safe diff; runtime имеет только `SELECT`/`INSERT`, а trigger запрещает `UPDATE`/`DELETE` даже владельцу;
- idempotent replay не дублирует audit event, failed command не оставляет audit data, а object-storage key и клиентские данные не включаются в diff;
- каждый HTTP-запрос получает проверенный или сгенерированный `X-Trace-Id`; тот же идентификатор возвращается клиенту, попадает в RFC 9457 body и связывает административный audit;
- MVC и Spring Security возвращают единый `application/problem+json`: stable code, status, safe detail, instance, trace ID и structured violations;
- отсутствие `If-Match` возвращает `428 PRECONDITION_REQUIRED`, stale version — `412 STALE_RESOURCE_VERSION`; 401, authorization 403 и CSRF 403 имеют отдельные стабильные коды;
- platform web utilities опубликованы как явный Spring Modulith named interface; architecture tests подтверждают отсутствие нового module cycle;
- блок 11/12 завершён: автоматизированный PostgreSQL performance fixture содержит 10 000 products, 40 000 variants, 10 000 media, typed values, пятиуровневое дерево и неравномерное распределение `70/20/10`;
- actual list/search/category/collection paths выполняются runtime role с `statement_timeout=2s`; `EXPLAIN (ANALYZE, BUFFERS)` проверяет shape и bounded execution критических планов;
- newest и primary-media paths используют соответствующие partial indexes; canonical detail использует expression index без sequential scan;
- category/search на этом объёме допускают cost-based sequential/hash plan только при строгом локальном ceiling; planner hints и искусственное отключение seq scan не используются;
- canonical repository query явно применяет `lower(canonicalSlug)`, устраняя расхождение между Spring Data ignore-case expression и PostgreSQL index;
- fixture является только test infrastructure, очищается после acceptance и никогда не попадает в Flyway/production seed; детали зафиксированы в `docs/persistence/CATALOG_QUERY_PLANS.md`;
- блок 12/12 завершён: финальный contract/client/container acceptance закрывает catalog vertical slice;
- все success и reusable RFC 9457 error responses каталога документируют `X-Trace-Id`; policy test не допускает drift относительно runtime filter;
- TypeScript Fetch source ZIP имеет собственный package template без generator repository placeholders и обязательный Gradle artifact gate;
- два clean build дали одинаковый SHA-256 `6143a4c2869263e8e69374028df3155b1ab990a28878bd18840aa66cbf62a9c2`; generated sources прошли `tsc --noEmit` frontend compiler;
- frontend handoff, browser session, CSRF, ETag/idempotency и fixture replacement описаны в `docs/api/FRONTEND_CATALOG_CLIENT.md`;
- supply-chain script и Hadolint прошли; pinned-digest distroless image работает как `nonroot:nonroot`;
- изолированный container smoke применил Flyway V1–V6, создал 16 tables, вернул health `UP` и успешные empty catalog representations; временный stack удалён;
- финальный `clean qualityGate` проходит со 116 тестами без failures/errors; branch coverage — 652/907 (71,9%), line coverage — 2 802/2 962 (94,6%);
- полный acceptance checklist находится в `docs/catalog/CATALOG_RELEASE_CHECKLIST.md`;
- исходное ТЗ зафиксировано в `docs/requirements/CATALOG_VERTICAL_SLICE.md`;
- ADR-0006 фиксирует public/admin split, composition boundaries, pagination, redirect и concurrency semantics;
- `docs/catalog/CATALOG_INVARIANTS.md` является компактным checklist для домена и review;
- category rules покрывают safe `HIDDEN` creation, versioning, cycle/orphan/depth/sibling-slug checks и stable navigation order;
- product rules покрывают `DRAFT → ACTIVE → ARCHIVED`, terminal archive, active-category/variant/primary-media completeness и запрет архивировать последний active variant;
- canonical slug history разрешается сразу в текущий slug; canonical/alias и SKU namespaces защищены от глобального повторного использования;
- `feature/catalog` fast-forward закрыта в `main`; production-only deferred gates не блокируют следующий локальный vertical slice;

GitLab activation остаётся обязательным deferred gate до первого shared remote, release или deployment.

## Завершённый этап 8

Этап 8 реализован в `feature/inventory` и fast-forward закрыт в `main`:

- утверждён план из 12 logical blocks с отдельными gates и commits;
- блок 1/12 завершён: ТЗ, ADR-0007, inventory invariants, public/warehouse OpenAPI и module boundaries зафиксированы;
- блок 2/12 завершён: framework-free immutable balance aggregate, typed identifiers, checked stock quantities и immutable physical movement model реализованы;
- receipts и reconciliations атомарно возвращают новую balance snapshot вместе с соответствующим ledger movement; reserve/release не искажают physical ledger, а commit создаёт исходящее движение;
- unit и jqwik property tests доказывают `0 <= reserved <= on_hand`, checked overflow/underflow, неизменяемость исходного snapshot и согласованность physical deltas;
- блок 3/12 завершён: immutable reservation aggregate содержит opaque owner reference, 1–100 normalized lines, status, expiry, extension count и optimistic version;
- duplicate lines агрегируются в first-seen order с checked addition; lifecycle допускает только `ACTIVE → COMMITTED|RELEASED|EXPIRED`, а expired reservation нельзя commit/extend до обработки worker;
- default TTL равен 15 минутам, одно strict extension считается от предыдущего deadline, terminal command replay остаётся ответственностью durable application idempotency до domain invocation;
- unit и jqwik property tests покрывают дедупликацию, overflow/limits, exact expiry boundary, terminal conflicts, extension limit и неизменяемость исходного snapshot;
- блок 4/12 завершён: Flyway V7 создаёт девять inventory-owned tables, seeded `PRIMARY` warehouse, explicit constraints/indexes/grants и PostgreSQL coordination records;
- database constraints являются последним barrier для negative/oversold balance, неверного movement sign/binding, reservation lifecycle shape, non-positive lines и невозможных events;
- movements, reservation events и inventory audit защищены runtime privileges и append-only triggers; balances/reservations/idempotency/leases изменяемы без runtime delete;
- inventory не имеет FK/JPA relation к catalog tables: UUIDv7 variant reference валидируется через named application boundary; schema rationale зафиксирован в `docs/persistence/INVENTORY_SCHEMA.md`;
- migration integration tests доказывают fresh V7, seeded warehouse, critical indexes, constraints, отсутствие cross-module FK и append-only barriers;
- блок 5/12 завершён: transactional stock facade и JDBC port атомарно lock/create balance, применяют domain mutation, optimistically обновляют snapshot и добавляют physical movement;
- receipt создаёт отсутствующий zero balance, reconciliation требует существующий balance и current version; оба пути проверяют signed ledger total до commit;
- интеграционные тесты доказывают atomic rollback при rejected movement, отсутствие side effects при stale version и равенство `on_hand` сумме immutable movements;
- обнаруженная SQL three-valued logic для UUID без RFC version закрыта отдельной Flyway V8 без изменения применённой V7: все inventory UUIDv7 checks требуют explicit `IS TRUE`;
- блок 6/12 завершён: named internal reservation contract нормализует lines, durable claim-ит owner-scoped idempotency key, batch-проверяет active catalog variants и создаёт reserve/event atomically;
- все balance rows создаются/блокируются в едином sorted `(warehouse_id, variant_id)` order до availability check; reserved-only updates не создают physical movements;
- PostgreSQL adapters сохраняют reservation root, normalized lines и immutable CREATED event; identical replay возвращает исходный aggregate, conflicting fingerprint ничего не меняет;
- real concurrent tests на virtual threads доказывают одного победителя за последнюю единицу, полный rollback multi-line failure и отсутствие deadlock для reversed inputs;
- блок 7/12 завершён: anonymous generated `GET /api/v1/inventory/availability` de-duplicates first-seen IDs и возвращает point-in-time `IN_STOCK/OUT_OF_STOCK` snapshot;
- catalog active view и inventory availability reader выполняют два bounded batch queries без N+1; unknown/inactive/missing balance всегда выглядит как `OUT_OF_STOCK`;
- response использует `Cache-Control: no-store`, trace header и никогда не содержит exact quantity, warehouse, reserved или on-hand fields;
- contract integration test подтверждает anonymous access, order, de-duplication, privacy и generated DTO shape;
- блок 8/12 завершён: generated protected warehouse API читает exact primary balance, принимает receipts и выполняет physical reconciliation;
- `/api/v1/admin/inventory/**` требует `WAREHOUSE_MANAGER` или `ADMIN`, verified email, MFA ACR и CSRF; GET также защищён, но не требует CSRF;
- административный склад дополнен обзором всех активных SKU, нулевых и существующих балансов, последних движений, свободной приёмкой и сверкой фактического остатка; catalog/inventory ownership сохранён через именованный application view;
- exact balance и каждый mutation result возвращают strong `ETag`; reconciliation требует current `If-Match`, различая `428` missing precondition и `412` stale version;
- receipt/reconciliation используют actor-scoped durable idempotency и collision-safe canonical fingerprint; одинаковая команда после более поздних движений возвращает исходные balance/movement/ETag без повторной мутации;
- Flyway V9 добавляет typed append-only `inventory_stock_command_results`, поэтому idempotent replay не хранит критический balance snapshot в JSON и не зависит от текущего состояния balance;
- contract integration tests подтверждают authorization, MFA, verified identity, CSRF, exact replay, conflict, inactive/missing variant и stable RFC 9457 codes;
- блок 9/12 завершён: named internal reservation contract поддерживает owner-scoped read, одно strict extension, all-or-nothing commit и release;
- lifecycle-команды сначала durable claim-ят idempotency key, затем блокируют reservation root и все balance rows в deterministic order; terminal race допускает ровно одного победителя;
- commit одновременно уменьшает `on_hand` и `reserved` каждой line и добавляет reservation-bound physical movements; release уменьшает только `reserved` без искажения physical ledger;
- Flyway V10 хранит immutable typed reservation command snapshots и backfill-ит существующие events/idempotency links, поэтому replay create/extend/commit/release возвращает исходный versioned result даже после следующего transition;
- integration tests подтверждают exact replay, extension limit, owner isolation, expired rejection, conflicting key, multi-line release и реальную commit/release race на virtual threads;
- блок 10/12 завершён: configurable scheduler вызывает bounded expiry use case, не сохраняя coordination state в памяти процесса;
- PostgreSQL lease использует `clock_timestamp()`, atomic insert/renew/expired-owner takeover и запрещает параллельному instance начинать batch до окончания lease;
- due reservations выбираются по partial expiry index в deterministic order через `FOR UPDATE SKIP LOCKED`; один transaction освобождает все line balances и добавляет `EXPIRED` event/result;
- scan interval, lease duration, batch size и instance identity задаются deployment properties с fail-fast bounds; lease обязан быть длиннее scan interval, batch ограничен 1–1000;
- integration tests подтверждают bounded `2 + 1 + 0` processing, idempotent repeat, сохранение physical `on_hand`, отсутствие movements, lease renewal и takeover по database time;
- OpenAPI добавляет anonymous batch availability и защищённые balance/receipt/reconciliation endpoints без browser reservation mutations;
- generated Java/TypeScript contracts, OpenAPI compatibility, architecture tests и полный `qualityGate` проходят;
- блок 11/12 завершён: успешные warehouse receipt/reconciliation создают append-only audit в той же транзакции с actor, movement time, warehouse/variant, reason/reference, correlation ID и allowlisted before/after quantity/version diff;
- replay, failed и stale/no-op commands не дублируют audit; integration acceptance подтверждает точную корреляцию, отсутствие лишних ключей и позитивный доступ обеих ролей `WAREHOUSE_MANAGER`/`ADMIN` при verified identity, MFA и CSRF;
- реальная expiry/commit гонка на virtual threads допускает один terminal outcome и сохраняет равенство `reserved` сумме active reservation lines без physical ledger drift;
- representative PostgreSQL fixture содержит 20 000 balances, 40 000 movements и 12 000 reservations; runtime `EXPLAIN (ANALYZE, BUFFERS)` под `statement_timeout=2s` проверяет четыре critical indexes и локальный ceiling 500 ms без planner hints;
- детали performance acceptance находятся в `docs/persistence/INVENTORY_QUERY_PLANS.md`;
- полный `clean qualityGate` блока 11 проходит со 173 тестами без failures/errors; branch coverage — 871/1 220 (71,4%), line coverage — 3 732/3 956 (94,3%);
- блок 12/12 завершён: artifact policy проверяет обе generated inventory API-группы, четыре operation ID и transport models;
- две clean-сборки TypeScript source ZIP дали одинаковый SHA-256 `4a6c77a3b9833eb630502cf69041700a5996b2916043c6ba96e0ac73a21909c1`; generated sources компилируются TypeScript 5.9.3;
- supply-chain policy и Hadolint проходят; pinned production image запускается как `nonroot:nonroot`;
- изолированный container smoke применил Flyway V1–V10 к PostgreSQL 18.4, создал 27 application tables, вернул health `UP` и privacy-safe `OUT_OF_STOCK`; временные resources удалены;
- frontend handoff находится в `docs/api/FRONTEND_INVENTORY_CLIENT.md`, полный acceptance — в `docs/inventory/INVENTORY_RELEASE_CHECKLIST.md`;
- `docs/DEVELOPER_GUIDE.md` является полным onboarding/development guide по runtime chains, решениям, классам, тестам, persistence и workflow;
- inventory vertical slice завершён 12/12 и fast-forward закрыт в `main`; следующим разрешён этап 9 — Customer, favorites и cart в отдельной `feature/customer-cart`;
- модель использует `on_hand`, `reserved`, вычисляемое `available`, immutable physical movement ledger и all-or-nothing reservations;
- public contract раскрывает только `IN_STOCK/OUT_OF_STOCK`; exact quantities остаются warehouse/internal data;
- исходное ТЗ находится в `docs/requirements/INVENTORY_VERTICAL_SLICE.md`.

## Следующий разрешённый этап

Local full-stack infrastructure acceptance закрыт: отдельный sibling-проект `amra-merch-market-infra` владеет единым Compose-проектом `amra-merch-market`, собирает frontend и backend из их Dockerfile и поднимает их вместе с PostgreSQL, Keycloak/PostgreSQL и MinIO. Контейнеры, сеть и persistent volumes имеют стабильные имена `amra-merch-market-*`; storefront, admin, backend health, OIDC discovery и MinIO health проверены через host HTTP endpoints. Локальные роли представлены импортируемыми пользователями `catalog-manager`, `warehouse-manager` и `amra-admin`.

Admin Catalog integration по ADR-0008 завершён: paged admin queries, presigned media lifecycle с local MinIO, generated client и frontend admin flow. Управляемый storefront banner slice включает protected CRUD, публикацию/архив, порядок и расписание в PostgreSQL, изображения в MinIO и anonymous read model витрины.

Customer/Favorites/Cart реализован как общий PostgreSQL-backed shopping context: гостевой токен хранится только в виде hash, избранное и корзина детерминированно объединяются после входа, а изменения корзины защищены `ETag`/`If-Match`. Backend повторно проверяет цену и остаток, возвращает понятные notices и очищает истёкшие guest/customer данные ограниченными пакетами. Личный кабинет поддерживает подтверждение email и сохранённые адреса. Frontend отказался от `localStorage` для shopping state и использует generated `CustomerApi`.

Pricing/Promotions завершён: V20 хранит непересекающиеся периоды RUB-цен для SKU,
percent/fixed-line/multi-buy акции и их targets. Расчёт tax-inclusive, использует
`HALF_UP`, выбирает одну лучшую акцию и детерминированно распределяет остаток.
Корзина возвращает сумму и скидку каждой строки; frontend показывает название акции.
Полный backend gate зелёный: 213 тестов, 90,3% lines и 70,3% branches.

Ordering/Checkout находится в работе. V21–V22 добавляют immutable order snapshots,
owner-scoped checkout commands, lifecycle events и outbox. Checkout выполняет
revalidation, создаёт reservation, фиксирует `PENDING`, коммитит stock и переводит
заказ в `CONFIRMED` в одной транзакции. Frontend сохраняет idempotency key до
однозначного ответа и открывает восстанавливаемый маршрут заказа. Payment boundary,
cancellation и single-shipment acceptance ещё не закрыты, поэтому этап 12 не разрешён.

Локальный admin browser flow завершает OIDC-контур: frontend проверяет backend-managed session до показа `/admin`, а конфигурируемый `amra.security.login-success-url` возвращает браузер из backend OAuth callback в административный интерфейс. Авторизация admin API по-прежнему выполняется только backend.

Security rejection логирует только stable code, HTTP method и request path; cookies, tokens, credentials и problem detail в telemetry не попадают.

SPA CSRF contract явно использует plain cookie/header request handler: browser читает `AMRA_CSRF` и возвращает то же значение в `X-AMRA-CSRF`; интеграционный тест проверяет реальный handshake без test-only CSRF post-processor.

Локальный Keycloak client `amra-backend` имеет явный realm-role mapper для ID token, access token и userinfo; backend session получает allowlisted RBAC roles из `realm_access.roles`, а не доверяет frontend.

Допустимые admin MFA ACR values конфигурируются через `AMRA_ADMIN_MFA_ACR_VALUES`: secure default принимает только `2`, а local Compose явно разрешает dev-level `0,1,2` для отладки без настройки MFA flow.

До его начала подготовлен проект [плана frontend/backend-интеграции](integration/FRONTEND_BACKEND_INTEGRATION_PLAN.md). Он фиксирует, что:

- frontend пока не вызывает backend и продолжает использовать product/profile fixtures и `localStorage`;
- Catalog/Search/Inventory можно подключать отдельным frontend slice после contract delivery и local seed;
- полноценные favorites/cart/profile требуют этапа 9, цены — этапа 10, checkout/orders — этапа 11, returns/integration flows — этапа 12;
- frontend foundation приведён к зелёным build, storefront tests и lint; новый admin code использует design tokens, CSS Modules и feature boundaries;
- local identity/data/media bootstrap и full-stack developer workflow являются обязательными integration gates;
- общий storefront plan остаётся последовательным; Admin Catalog integration отдельно утверждён владельцем 20 августа 2026 года.

Результаты проверки контекстных файлов и найденные contract/frontend gaps находятся в [CONTEXT_AUDIT.md](integration/CONTEXT_AUDIT.md).

## Отложено до production readiness

- production orchestrator/platform;
- Secret Manager provider;
- real payment/delivery/notification providers;
- legal consent requirements.

## Важная локальная особенность

В системном окружении Codex JDK не установлен. Initial verification выполнена временным JDK 25 вне repository; JDK binaries и Gradle cache в Git не входят.
