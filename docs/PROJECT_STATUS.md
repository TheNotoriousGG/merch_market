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
- локально подтверждены reproducible JAR, Hadolint, digest policy, multi-stage image build и health smoke под non-root/read-only/cap-drop/no-new-privileges;
- реальный GitLab pipeline и immutable Registry policy ожидают создания/подключения remote project и runner.

## Следующий разрешённый этап

Завершить exit gate этапа 3:

- создать или подключить GitLab 19.x Ultimate project и Container Registry;
- применить project settings из `docs/operations/GITLAB_DELIVERY.md`;
- отправить feature-ветку и получить полный зелёный MR pipeline;
- подтвердить SBOM/provenance/security reports, immutable commit tag и smoke одного digest;
- выполнить review, rebase и fast-forward merge.

Этап 4 OpenAPI не начинать до прохождения реального GitLab exit gate этапа 3.

## Отложено до production readiness

- production orchestrator/platform;
- Secret Manager provider;
- real payment/delivery/notification providers;
- legal consent requirements.

## Важная локальная особенность

В системном окружении Codex JDK не установлен. Initial verification выполнена временным JDK 25 вне repository; JDK binaries и Gradle cache в Git не входят.
