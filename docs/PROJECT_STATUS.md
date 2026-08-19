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

## Следующий разрешённый этап после закрытия текущего

Этап 7 — `feature/catalog`:

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
