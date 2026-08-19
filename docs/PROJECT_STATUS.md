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

## Текущая точка

Этап 1 завершён initial commit `chore: initialize backend project` в `main`.

## Следующий разрешённый этап

Этап 2 — `feature/engineering-foundation`:

- formatting и static analysis;
- JUnit/AssertJ/Mockito/jqwik/ArchUnit/Testcontainers/WireMock foundation;
- Spring Modulith verification;
- coverage/mutation foundation;
- ADR и merge request templates.

Не начинать OpenAPI, PostgreSQL, security или business modules до прохождения соответствующих gates из `IMPLEMENTATION_PLAN.md`.

## Отложено до production readiness

- production orchestrator/platform;
- Secret Manager provider;
- real payment/delivery/notification providers;
- legal consent requirements.

## Важная локальная особенность

В системном окружении Codex JDK не установлен. Initial verification выполнена временным JDK 25 вне repository; JDK binaries и Gradle cache в Git не входят.
