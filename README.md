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

## Проверки качества

```shell
./gradlew qualityGate
./gradlew pitest
./gradlew spotlessApply
```

`qualityGate` запускает formatting check, Error Prone/NullAway compilation, Checkstyle, Javadoc doclint, tests, architecture verification и JaCoCo thresholds. PIT запускается отдельно для critical modules, чтобы mutation testing оставался явным и измеримым этапом.

Текущее состояние и следующий разрешённый этап находятся в [PROJECT_STATUS.md](docs/PROJECT_STATUS.md). Markdown обновляется вместе с каждым изменением решения или поведения.
