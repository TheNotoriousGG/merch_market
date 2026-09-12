# amra-merch-market-backend

Отдельный production-oriented backend проекта «Амра Шоп».

Проект инициализирован и развивается последовательными short-lived feature-ветками. Новый разработчик начинает с полного [DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md). Принятые решения находятся в [реестре решений](docs/DECISION_REGISTER.md), инженерные правила — в [ENGINEERING_CHARTER.md](docs/ENGINEERING_CHARTER.md), системный контекст — в [SYSTEM_CONTEXT.md](docs/architecture/SYSTEM_CONTEXT.md), последовательность этапов и gates — в [IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

Текущее состояние: platform, identity/access, Catalog и Inventory завершены; следующим разрешён этап Customer/Favorites/Cart. Frontend пока остаётся прототипом на fixtures и `localStorage`, поэтому команды ниже поднимают backend и infrastructure, а не готовый full-stack магазин. Последовательность полноценного соединения проектов находится в [плане frontend/backend-интеграции](docs/integration/FRONTEND_BACKEND_INTEGRATION_PLAN.md), результаты проверки контекста — в [аудите](docs/integration/CONTEXT_AUDIT.md).

## Зафиксированная база

- Java 25;
- виртуальные потоки;
- Spring Boot 4.1.0;
- Gradle Wrapper 9.6.1;
- Spring MVC на virtual threads;
- Gradle с Kotlin DSL;
- отдельный репозиторий от frontend;
- OpenAPI-first;
- модульный монолит на Spring Modulith с architecture tests;
- managed PostgreSQL и Flyway SQL migrations;
- Keycloak/OIDC и backend-managed HttpOnly sessions;
- production-ready качество, тестирование, наблюдаемость и контейнеризация.

## Локальная проверка

Требуется JDK 25. Системная установка Gradle не нужна — используется Wrapper.

```shell
./gradlew clean build
./gradlew bootRun
```

Приложение включает virtual threads. Публичный API добавляется OpenAPI-first вертикальными срезами.

Canonical OpenAPI contract находится в `src/main/openapi`. Discovery endpoint доступен по `GET /api/v1/`. Документация выключена по умолчанию и для local/test включается свойством `amra.api-docs.enabled=true`, после чего modular YAML доступен с `/internal/api-docs/openapi.yaml`.

## Проверки качества

```shell
./gradlew qualityGate
./gradlew pitest
./gradlew spotlessApply
./gradlew openApiValidate checkOpenApiCompatibility
./gradlew generateJavaApi packageTypeScriptClient verifyTypeScriptClientArtifact
```

`qualityGate` запускает formatting check, Error Prone/NullAway compilation, Checkstyle, Javadoc doclint, tests, architecture verification и JaCoCo thresholds. PIT запускается отдельно для critical modules, чтобы mutation testing оставался явным и измеримым этапом.

`packageTypeScriptClient` создаёт воспроизводимый versioned source ZIP в `build/distributions`, а `verifyTypeScriptClientArtifact` проверяет его metadata и обязательную public/admin catalog/inventory surface. До активации package registry это локальный source artifact, не опубликованный npm package. Порядок frontend-интеграции и browser security requirements описаны в [FRONTEND_CATALOG_CLIENT.md](docs/api/FRONTEND_CATALOG_CLIENT.md) и [FRONTEND_INVENTORY_CLIENT.md](docs/api/FRONTEND_INVENTORY_CLIENT.md).

Integration tests используют PostgreSQL 18.4 через Testcontainers, поэтому для полного `qualityGate` нужен работающий Docker daemon. In-memory database намеренно не используется.

## Локальный full-stack

```shell
cd ../amra-merch-market-infra
docker compose up --build --detach
```

Compose принадлежит отдельному sibling-проекту `amra-merch-market-infra`, собирает backend через `Dockerfile.compose` и не требует локальных Java/Node. `amra_owner` используется только bootstrap-контейнером, `amra_migrator` — Flyway, `amra_runtime` — приложением. Соглашения описаны в [DATABASE_CONVENTIONS.md](docs/persistence/DATABASE_CONVENTIONS.md), recovery assumptions — в [DATABASE_RECOVERY.md](docs/operations/DATABASE_RECOVERY.md).

Локальный Keycloak доступен на `http://localhost:8081`, использует отдельную PostgreSQL и импортирует realm `amra-shop` без тестовых пользователей. Авторизация начинается с `/oauth2/authorization/keycloak`; состояние браузерной сессии доступно по `GET /api/v1/session`. Контракт ролей, MFA, CSRF, CORS и отзыва сессий описан в [IDENTITY_ACCESS.md](docs/security/IDENTITY_ACCESS.md).

Воспроизводимые local users, catalog/inventory seed, media fixtures, frontend API client и единый full-stack start ещё не реализованы. Они являются отдельными gates интеграционного плана; README не выдаёт ручную тестовую настройку за готовый workflow.

Container build и обязательные GitLab project settings описаны в [GITLAB_DELIVERY.md](docs/operations/GITLAB_DELIVERY.md). Подключение GitLab remote/runner и registry временно отложено по ADR-0002; локальные gates и container checks остаются обязательными.

Текущее состояние и следующий разрешённый этап находятся в [PROJECT_STATUS.md](docs/PROJECT_STATUS.md). Markdown обновляется вместе с каждым изменением решения или поведения.
