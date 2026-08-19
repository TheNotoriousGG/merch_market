# Инженерный устав backend

Статус: утверждён.

Обновлено: 19 августа 2026 года.

## Цель

Создать быстрый, отказоустойчивый, масштабируемый и поддерживаемый backend интернет-магазина «Амра Шоп», готовый к контейнерному production-развёртыванию. Качество архитектуры, данных и истории изменений важнее скорости выдачи большого объёма кода.

## Технологическая база

- Проект и отдельный репозиторий: `amra-merch-market-backend`.
- Java 25 и virtual threads для подходящих blocking I/O workloads.
- Spring Boot 4.1.x; точная latest GA patch-версия закрепляется и проверяется при инициализации.
- Spring MVC; WebFlux/R2DBC не входят в выбранный stack.
- Gradle Kotlin DSL, Wrapper, reproducible build и dependency locking.
- Managed PostgreSQL в production; Flyway SQL migrations; Spring Data JPA/Hibernate с OSIV off.
- OpenAPI-first: generated API interfaces, transport DTO, error models и TypeScript client; handwritten domain/JPA models.
- Модульный монолит: Spring Modulith + ArchUnit, hexagonal boundaries внутри модулей.
- GitLab CI/Registry; multi-stage Dockerfile и non-root distroless Java 25 runtime.

Полный набор решений и исключений находится в `docs/DECISION_REGISTER.md`.

## Архитектурные принципы

- Сначала простой модульный монолит. Микросервис выделяется только по измеримой организационной или эксплуатационной причине.
- Domain не зависит от framework, persistence и generated transport code.
- HTTP, PostgreSQL, object storage, identity provider и внешние сервисы являются adapters.
- Модуль владеет своим поведением и данными; обход public application API запрещён.
- Business invariants не размещаются в controllers, mappers, generated code и JPA callbacks.
- Transactions ограничены use-case boundaries; долгие external calls не удерживают DB transaction.
- Database constraints и atomic operations защищают strong consistency; outbox обеспечивает reliable post-commit effects.
- Масштабирование не зависит от локальной памяти instance. Concurrency всегда bounded, включая virtual threads.
- Resilience применяется к конкретным failure modes, а не декоративно.
- API backward-compatible внутри major version и использует корректную HTTP semantics.
- PII и secrets минимизируются, классифицируются и не попадают в telemetry.
- Долгосрочное решение оформляется обновлением реестра или ADR одновременно с code.

## Код и документация

- Имена classes, methods, variables, tables, columns, constraints и indexes отражают domain meaning.
- Код compact и readable; SOLID/DRY применяются прагматично, без premature abstractions.
- DTO, domain objects и persistence entities не смешиваются.
- Mapping выполняется MapStruct и не содержит business logic.
- Lombok ограничен whitelist из реестра; entity identity и string representation реализуются осознанно.
- Javadoc описывает contract, invariant, thread/transaction semantics и причины нетривиального решения, а не пересказывает реализацию.
- Документы обновляются в той же feature-ветке, где меняется соответствующее решение или поведение.

## Тестовая стратегия

- Unit: domain rules, use cases, policies и error paths.
- Property-based: money, rounding, promotions, order transitions и inventory invariants.
- Slice: web, persistence, serialization и security configuration.
- Integration: PostgreSQL и реальные infrastructure dependencies через Testcontainers.
- Contract: OpenAPI generation, breaking-change detection и фактические HTTP responses.
- Architecture: module boundaries, dependency direction, forbidden access и cycles.
- Migration: schema from scratch, supported upgrade paths и expand/contract.
- Failure-mode: timeout, retry, duplicate delivery, idempotency, lock conflict и graceful shutdown.
- Performance: сценарии формируются из SLO и проверяются на realistic data перед production.

Coverage — risk-based: ≥80% line и ≥70% branch overall; critical domain invariants покрываются полностью. Critical modules проходят mutation testing. Процент не заменяет качество assertions и scenarios.

## Quality gates

Минимальный merge pipeline:

1. formatting и static analysis;
2. compilation и Javadoc doclint;
3. unit/property/architecture tests;
4. OpenAPI lint, validation, breaking-change и generated-diff checks;
5. slice/integration/contract/migration tests;
6. coverage и mutation gates согласно области изменения;
7. container build, SBOM, dependency/container scanning;
8. smoke test собранного immutable image.

Намеренное нарушение каждого blocking gate должно быть доказуемо тестом pipeline configuration.

## Git-дисциплина

- Сначала один чистый initial commit в `main`.
- После него вся реализация идёт в short-lived `feature/<name>` branches.
- Conventional Commits; один commit — один законченный logical block.
- Formatting, refactoring и behaviour change не смешиваются без необходимости.
- Перед commit выполняются применимые local gates; merge возможен только через зелёный GitLab pipeline и review.
- Strategy — rebase + fast-forward; history должна объяснять путь разработки и поддерживать safe rollback.
- Нельзя коммитить generated drift, secrets, local data и unrelated changes.

## Стоп-условия

- Не начинать vertical slice без утверждённого contract и domain invariants.
- Не выбирать production platform, Secret Manager или real provider молча: отложенное решение закрывается до первого зависящего этапа.
- Не добавлять cache, broker, feature flags, jOOQ или новый infrastructure component без измеримой необходимости и записи решения.
- Не ослаблять test/security/architecture gate ради прохождения pipeline без documented exception.
- Не проводить destructive schema change в одном release.
