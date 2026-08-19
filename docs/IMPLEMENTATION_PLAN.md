# План реализации backend

Статус: утверждён владельцем проекта 19 августа 2026 года.

Обновлено: 19 августа 2026 года.

План состоит из последовательных gates. Следующий этап не начинается, пока критерии выхода предыдущего не выполнены. Временное исключение local-only GitLab activation описано ADR-0002 и не распространяется на release/deployment. Каждый этап после initial commit выполняется в отдельной short-lived feature-ветке и завершается документацией, проверками и logical Conventional Commits.

## Этап 0. Architecture baseline — до Git init

- Проверить соответствие `ENGINEERING_CHARTER.md` и `DECISION_REGISTER.md` утверждённым решениям.
- Провести final review MVP boundaries и отложенных решений.
- Зафиксировать context diagram и initial ADR модульного монолита.
- Убедиться, что production-only choices не блокируют local initialization.

Выход: нет открытых P0; exclusions и решения «до production» перечислены; владелец утвердил план.

## Этап 1. Инициализация и единственный initial commit

Ветка: `main`.

- По официальным источникам проверить latest GA patch Spring Boot 4.1.x и совместимые Gradle/plugins для Java 25.
- Создать Spring MVC application с Java toolchain 25, Gradle Wrapper и Kotlin DSL.
- Добавить module/package placeholders без business implementation.
- Добавить `.editorconfig`, ignore files, reproducible build, dependency verification/locking и run documentation.
- Инициализировать Git только после проверки contents.
- Запустить clean build из fresh checkout.

Commit: `chore: initialize backend project`.

Выход: `main` содержит один reproducible initial commit; working tree clean.

## Этап 2. Engineering foundation

Ветка: `feature/engineering-foundation`.

- Spotless, Checkstyle, Error Prone/NullAway, SonarQube integration и Javadoc doclint после Java 25 compatibility verification.
- JUnit 6, AssertJ, Mockito, jqwik, ArchUnit, Testcontainers и WireMock.
- Risk-based coverage и mutation testing foundation.
- Spring Modulith verification и architecture tests для module/package rules.
- ADR template, merge request template и local verification task.

Логические commits: build conventions; test foundation; architecture gates; documentation templates.

Выход: намеренное нарушение каждого local gate даёт предсказуемую ошибку.

## Этап 3. GitLab CI и supply chain

Ветка: `feature/gitlab-ci`.

- Stages для lint/build/test/integration/security/container/smoke.
- Dependency/container scanning, SBOM и immutable artifacts.
- Multi-stage Dockerfile, pinned digests, non-root distroless Java 25 runtime.
- Публикация image в GitLab Container Registry.
- Protected `main`, required pipeline/review и rebase + fast-forward policy.

Выход: локально подтверждены pipeline configuration, reproducible JAR, hardened image и smoke. По ADR-0002 запуск в GitLab Registry/runner временно отложен, но обязателен до первого shared remote, release или deployment.

## Этап 4. OpenAPI contract foundation

Ветка: `feature/openapi-foundation`.

- Модульная OpenAPI specification для `/api/v1`.
- Problem Details, nullability, ISO UTC, pagination/cursor, filtering, sorting, idempotency и ETag conventions.
- Lint, validation и breaking-change comparison с `main`.
- Generation Java API interfaces/transport DTO/error models и TypeScript client.
- Публикация frontend client в GitLab Package Registry.
- Minimal protected documentation endpoint и end-to-end contract test.

Техническое решение: OpenAPI 3.0.3, OpenAPI Generator 7.22.0 и OpenAPI Diff 2.1.7 по ADR-0003. Пока действует ADR-0002, TypeScript client генерируется и упаковывается локально, а публикация в GitLab Package Registry остаётся deferred gate.

Выход: generated code не редактируется вручную; breaking change `/api/v1` блокирует merge.

## Этап 5. PostgreSQL и persistence foundation

Ветка: `feature/postgresql-foundation`.

- Local Docker Compose и Testcontainers configuration.
- Flyway, schema `amra_shop`, owner/migration/runtime roles и explicit `search_path`.
- UUIDv7, audit timestamps, optimistic versioning и naming conventions.
- OSIV off; transaction, query timeout и fetch policies.
- Migration tests from scratch и expand/contract template.
- PITR/backup/restore assumptions и explain-plan review rules.

Техническое решение: PostgreSQL 18.4, Flyway 12.4.0 из Spring Boot BOM, Hibernate 7.4.1 и Testcontainers 2.0.5. Role/schema/bootstrap boundaries описаны ADR-0004.

Выход: schema создаётся только migrations; application не стартует при incompatible schema.

## Этап 6. Identity, sessions и authorization

Ветка: `feature/identity-access`.

- Keycloak OIDC integration без хранения passwords и доступа JS к tokens.
- Backend-managed Secure HttpOnly session через Spring Session JDBC, CSRF и CORS allowlist.
- Permissions/roles; admin endpoints требуют MFA claims.
- Session lifetime/revocation и verified-email contracts.
- Security slice/integration tests: denial, expiry и role escalation attempts.

Техническое решение: Keycloak 26.7.0, Spring Security 7.1 OIDC Authorization Code + PKCE, Spring Session JDBC и application allowlist ролей по ADR-0005. Principal/revocation key — OIDC `sub`; admin gate требует `ROLE_ADMIN` и ACR level `2`.

Выход: customer/admin security paths доказаны тестами; secrets отсутствуют в repository.

## Этап 7. Catalog vertical slice

Ветка: `feature/catalog`.

Статус: завершён 19 августа 2026 года; acceptance evidence находится в `docs/catalog/CATALOG_RELEASE_CHECKLIST.md`.

Подробное продуктовое и техническое ТЗ: `docs/requirements/CATALOG_VERTICAL_SLICE.md`.

- OpenAPI: categories, products, variants, attributes, media metadata и search/filtering.
- Domain: category tree invariants, lifecycle, slug aliases, immutable SKU, publish validation.
- Persistence: relational model, constraints, indexes, FTS + `pg_trgm`, object-storage port.
- Admin API: ETag/If-Match, permissions и audit event.
- Tests: unit/property, persistence, recursive queries, search relevance, N+1 и contract.

Выход: published catalog публичен; draft/archive меняются только через protected admin contract.

## Этап 8. Inventory vertical slice

Ветка: `feature/inventory`.

Статус: блоки 1–8/12 завершены, следующим разрешён блок 9/12. Подробное ТЗ находится в `docs/requirements/INVENTORY_VERTICAL_SLICE.md`, consistency boundary — в ADR-0007.

- Блок 1: контракт, архитектурные границы и проверяемые invariants.
- Блок 2: framework-free immutable balance aggregate, checked stock quantities и append-only physical movement model с unit/property tests.
- Блок 3: lifecycle aggregate резервирования, terminal-state/idempotency semantics и unit/property tests.
- Блок 4: Flyway V7 inventory schema, constraints, indexes и migration tests.
- Блок 5: atomic balance/movement persistence, locking и ledger reconciliation.
- Блок 6: all-or-nothing reservation application contract, deterministic locking и concurrency acceptance.
- Блок 7: anonymous bounded batch availability API без раскрытия exact quantities.
- Блок 8: protected warehouse balance, receipt и reconciliation API.
- Блок 9: commit, release и одно strict extension reservation contracts.
- Блок 10: bounded expiry worker с PostgreSQL lease.
- Блок 11: inventory audit, security/race/query-plan acceptance.
- Блок 12: generated client, container, reproducibility и documentation closeout.

- Balance, immutable movement ledger и atomic reservation.
- Запрет negative stock/oversell на transaction/constraint levels.
- TTL 15 минут, одно strict extension и idempotent expiry/release job с PostgreSQL lease.
- Warehouse/admin commands и audit.
- Concurrency, duplicate, expiry race и ledger reconciliation tests.

Выход: parallel operations не нарушают stock invariants.

## Этап 9. Customer, favorites и cart

Ветка: `feature/customer-cart`.

- Minimal profile, verified email и saved addresses.
- Anonymous profile, guest favorites и deterministic merge after login.
- PostgreSQL cart, secure guest identity, optimistic locking и retention cleanup.
- Price/stock revalidation и contract уведомления об изменениях.
- Guest/auth merge, expiry, concurrency и access-control tests.

Выход: guest/auth flows одинаково работают на нескольких backend instances.

## Этап 10. Pricing и promotions

Ветка: `feature/pricing`.

- SKU base-price periods, RUB, tax-inclusive calculation и immutable result.
- Bounded promotion types без generic rule engine; non-stacking best offer.
- Line-level `HALF_UP` и deterministic residual allocation.
- jqwik properties, boundary dates и promotion conflict tests.

Выход: одинаковый input создаёт объяснимый и reproducible pricing result.

## Этап 11. Ordering и checkout

Ветка: `feature/ordering`.

- Order state machine, public number и immutable snapshots.
- Idempotent checkout: revalidate → reserve → pending → payment → confirmed.
- Guest-order scoped token, cancellation rules и single-shipment model.
- Transactional outbox и audit trail.
- Retry, duplicate command, transaction/outbox crash и invalid-transition tests.

Выход: retry или crash не создаёт duplicate order и не теряет stock.

## Этап 12. Returns и integration ports

Ветка: `feature/order-integrations`.

- Full/line return workflow и independent refund status.
- Payment, delivery и notification ports с controllable fake adapters.
- Signed, replay-protected, idempotent webhook ingestion.
- Async post-commit notification и fake inbox.
- Provider reconciliation не добавлять до выбора real provider.

Выход: success/failure/timeout/replay воспроизводимы без external vendors.

## Этап 13. Administration и audit

Ветка: `feature/administration`.

- `/api/v1/admin/**` с separate DTO, permissions и business rate limits.
- Append-only audit trail: actor/time/entity/reason/correlation/safe diff.
- Отдельная защита и audit просмотра audit data.
- Tests для stale edit 412, privilege boundaries и PII redaction.

Выход: admin change авторизован, concurrency-safe и traceable.

## Этап 14. Observability и resilience

Ветка: `feature/operability`.

- OpenTelemetry, Micrometer, structured JSON logs и correlation IDs.
- Liveness/readiness; PostgreSQL влияет на readiness, noncritical adapters — нет.
- Graceful shutdown, bounded virtual-thread concurrency, timeout/retry/circuit-breaker/bulkhead policies.
- Gateway/backend rate-limit contracts и 429 + `Retry-After`.
- Failure-mode tests и baseline dashboards/alerts specification.

Выход: critical failure modes видимы и дают ожидаемую деградацию.

## Этап 15. Release candidate и production readiness

Ветка: `feature/production-readiness`.

- Load/soak tests относительно 50 RPS sizing и 99.9% availability target.
- Critical SQL plans на realistic dataset.
- Restore drill и подтверждение RPO ≤5 минут/RTO ≤30 минут на выбранной platform.
- Закрыть production platform, Secret Manager, TLS/certificates, Keycloak HA и retention choices.
- Уточнить legal requirements; consent subsystem добавлять только при необходимости.
- Runbooks: deploy, migration, rollback, incident, backup/restore, session/key rotation.

Выход: production-readiness checklist утверждён; release candidate использует тот же image, что прошёл staging.

## Git-ритм каждого этапа

1. Сверить этап с decision register и создать/обновить ADR.
2. Создать feature branch от актуального `main`.
3. Сначала зафиксировать contract и testable invariants.
4. Реализовать minimal complete block без unrelated changes.
5. Обновить документацию в той же branch.
6. Пройти local gates и проверить generated/migration diff.
7. Создать logical Conventional Commits.
8. Пройти GitLab CI/review, выполнить rebase + fast-forward merge. Пока действует local-only исключение ADR-0002 — пройти все доступные local gates и выполнить локальный fast-forward без публикации.
9. Проверить green `main`, reproducible artifacts и актуальность docs.

## Сопровождение Markdown

- `ENGINEERING_CHARTER.md` меняется при изменении engineering principles.
- `DECISION_REGISTER.md` меняется при принятии, отмене или откладывании устойчивого решения.
- `IMPLEMENTATION_PLAN.md` меняется при перестановке stages, gates или scope.
- ADR добавляется для решения с alternatives и long-term consequences.
- Documentation входит в Definition of Done и обновляется вместе с code, а не после него.
