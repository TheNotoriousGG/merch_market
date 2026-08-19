# amra-merch-market-backend

Отдельный production-oriented backend проекта «Амра Шоп».

Проект инициализирован и развивается последовательными short-lived feature-ветками. Принятые решения находятся в [реестре решений](docs/DECISION_REGISTER.md), инженерные правила — в [ENGINEERING_CHARTER.md](docs/ENGINEERING_CHARTER.md), системный контекст — в [SYSTEM_CONTEXT.md](docs/architecture/SYSTEM_CONTEXT.md), последовательность этапов и gates — в [IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

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

Приложение включает virtual threads. По умолчанию наружу доступны только actuator endpoints `health` и `info`; бизнес-API добавляются OpenAPI-first вертикальными срезами.

Canonical OpenAPI contract находится в `src/main/openapi`. Discovery endpoint доступен по `GET /api/v1/`. Документация выключена по умолчанию и для local/test включается свойством `amra.api-docs.enabled=true`, после чего modular YAML доступен с `/internal/api-docs/openapi.yaml`.

## Проверки качества

```shell
./gradlew qualityGate
./gradlew pitest
./gradlew spotlessApply
./gradlew openApiValidate checkOpenApiCompatibility
./gradlew generateJavaApi packageTypeScriptClient
```

`qualityGate` запускает formatting check, Error Prone/NullAway compilation, Checkstyle, Javadoc doclint, tests, architecture verification и JaCoCo thresholds. PIT запускается отдельно для critical modules, чтобы mutation testing оставался явным и измеримым этапом.

Integration tests используют PostgreSQL 18.4 через Testcontainers, поэтому для полного `qualityGate` нужен работающий Docker daemon. In-memory database намеренно не используется.

## Локальная PostgreSQL

```shell
cp .env.example .env
# Заменить все три local password.
docker compose up -d postgres
set -a && . ./.env && set +a
./gradlew bootRun
```

`amra_owner` используется только bootstrap-контейнером, `amra_migrator` — Flyway, `amra_runtime` — приложением. Соглашения описаны в [DATABASE_CONVENTIONS.md](docs/persistence/DATABASE_CONVENTIONS.md), recovery assumptions — в [DATABASE_RECOVERY.md](docs/operations/DATABASE_RECOVERY.md).

Container build и обязательные GitLab project settings описаны в [GITLAB_DELIVERY.md](docs/operations/GITLAB_DELIVERY.md). Подключение GitLab remote/runner и registry временно отложено по ADR-0002; локальные gates и container checks остаются обязательными.

Текущее состояние и следующий разрешённый этап находятся в [PROJECT_STATUS.md](docs/PROJECT_STATUS.md). Markdown обновляется вместе с каждым изменением решения или поведения.
