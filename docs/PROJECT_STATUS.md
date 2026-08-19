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

Этап 2 завершён и fast-forward слит в `main`:

- `37f6c12 build: enforce formatting and static analysis`;
- `b0647fd test: establish architecture and mutation testing`;
- `d0791bf docs: document engineering quality gates`.

Этап 3 реализуется в `feature/gitlab-ci`:

- `87dc228 build: add hardened layered container image`;
- `e0ad22d ci: add verified container supply chain`;
- `5b05c9d docs: define GitLab delivery controls`;
- локально подтверждены reproducible JAR, Hadolint, digest policy, multi-stage image build и health smoke под non-root/read-only/cap-drop/no-new-privileges;
- владелец проекта отложил подключение GitLab; границы исключения зафиксированы ADR-0002.

## Следующий разрешённый этап

Этап 4 — `feature/openapi-foundation`:

- модульная OpenAPI specification `/api/v1`;
- единые API conventions и RFC 9457 Problem Details;
- lint, validation и breaking-change gates;
- generation Java interfaces/DTO и TypeScript client;
- minimal contract endpoint и end-to-end contract test.

GitLab activation остаётся обязательным deferred gate до первого shared remote, release или deployment.

## Отложено до production readiness

- production orchestrator/platform;
- Secret Manager provider;
- real payment/delivery/notification providers;
- legal consent requirements.

## Важная локальная особенность

В системном окружении Codex JDK не установлен. Initial verification выполнена временным JDK 25 вне repository; JDK binaries и Gradle cache в Git не входят.
