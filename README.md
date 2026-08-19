# amra-merch-market-backend

Отдельный production-oriented backend проекта «Амра Шоп».

Реализация намеренно ещё не инициализирована. Архитектурный опрос и план утверждены: принятые решения находятся в [реестре решений](docs/DECISION_REGISTER.md), инженерные правила — в [ENGINEERING_CHARTER.md](docs/ENGINEERING_CHARTER.md), системный контекст — в [SYSTEM_CONTEXT.md](docs/architecture/SYSTEM_CONTEXT.md), последовательность feature-веток и gates — в [IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).

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

Текущее состояние и следующий разрешённый этап находятся в [PROJECT_STATUS.md](docs/PROJECT_STATUS.md). Markdown обновляется вместе с каждым изменением решения или поведения.
