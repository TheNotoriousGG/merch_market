# Текущее состояние backend

Обновлено: 19 августа 2026 года.

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

Этапы 2–4 завершены и fast-forward слиты в `main`. GitLab activation отложена по ADR-0002 без ослабления локальных gates.

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

Этап 5 завершён в `feature/postgresql-foundation`:

- `ed66702 build: establish PostgreSQL persistence runtime`;
- `74319d0 test: verify PostgreSQL migration boundaries`;
- PostgreSQL 18.4 Compose/Testcontainers image закреплён digest;
- `amra_owner`, `amra_migrator` и `amra_runtime` разделены;
- schema `amra_shop`, Flyway-only migrations, Hibernate validate и OSIV off;
- runtime DDL запрещён, Hikari pool и statement/lock timeouts ограничены;
- integration tests подтверждают fresh migration, idempotency, checksum failure, permissions и UUIDv7;
- ADR-0004, database conventions и recovery assumptions поддерживают межсессионный контекст;
- local Compose config, shell syntax и полный offline `qualityGate` проходят успешно.

Этап 6 завершён в `feature/identity-access`:

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

## Текущий этап

Этап 7 — `feature/catalog`:

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
- исходное ТЗ зафиксировано в `docs/requirements/CATALOG_VERTICAL_SLICE.md`;
- ADR-0006 фиксирует public/admin split, composition boundaries, pagination, redirect и concurrency semantics;
- `docs/catalog/CATALOG_INVARIANTS.md` является компактным checklist для домена и review;
- category rules покрывают safe `HIDDEN` creation, versioning, cycle/orphan/depth/sibling-slug checks и stable navigation order;
- product rules покрывают `DRAFT → ACTIVE → ARCHIVED`, terminal archive, active-category/variant/primary-media completeness и запрет архивировать последний active variant;
- canonical slug history разрешается сразу в текущий slug; canonical/alias и SKU namespaces защищены от глобального повторного использования;
- полный `qualityGate` проходит со 107 тестами без failures/errors; branch coverage — 605/849 (71,3%) при обязательном пороге 70%;
- следующий блок 10/12: append-only audit, централизованное RFC 9457 error mapping и завершение security/precondition semantics;

- OpenAPI categories/products/variants и typed filtering;
- category/product/SKU domain invariants;
- PostgreSQL catalog model, FTS/search port и admin ETag contract;
- unit/property/persistence/contract/N+1 tests.

GitLab activation остаётся обязательным deferred gate до первого shared remote, release или deployment.

## Отложено до production readiness

- production orchestrator/platform;
- Secret Manager provider;
- real payment/delivery/notification providers;
- legal consent requirements.

## Важная локальная особенность

В системном окружении Codex JDK не установлен. Initial verification выполнена временным JDK 25 вне repository; JDK binaries и Gradle cache в Git не входят.
