# Стратегия тестирования

Статус: утверждённый foundation.

Обновлено: 19 августа 2026 года.

## Цель

Тесты защищают business invariants и integration contracts, а не детали реализации. Набор должен быстро находить regression, давать точную диагностику и оставаться воспроизводимым локально и в CI.

## Уровни

| Уровень | Назначение | Основные инструменты |
| --- | --- | --- |
| Unit | Domain policies, value objects, use cases и error paths без Spring context | JUnit 6, AssertJ, Mockito только для ports |
| Property-based | Money, rounding, promotions, transitions и inventory invariants на широком input space | jqwik 1.10.1 |
| Architecture | Module graph, public/internal boundaries, layer direction и cycles | Spring Modulith 2.1.0, ArchUnit 1.5.0 |
| Slice | Web, serialization, validation, persistence и security configuration | Spring Boot test slices |
| Integration | PostgreSQL, migrations и infrastructure adapters | Testcontainers 2.0.5 |
| External contract | Failure, timeout, replay и protocol behaviour adapters | WireMock 3.13.2, controllable fakes |
| Contract | OpenAPI generation и фактическая HTTP semantics | Добавляется на contract foundation stage |
| Mutation | Способность tests обнаруживать изменение critical domain logic | PIT 1.25.9 |
| Performance | Latency, throughput, query plans и saturation | Добавляется перед production readiness |

## PostgreSQL integration

- Integration tests используют PostgreSQL 18.4 Testcontainer с теми же owner/migrator/runtime boundaries, что local environment.
- H2 и другие in-memory substitutes не применяются для persistence behaviour.
- Fresh-container test проверяет Flyway from scratch, повторный no-op migrate, runtime privileges/timeouts, PostgreSQL major и UUIDv7.
- Controlled checksum mismatch доказывает отказ migration/startup gate при несовместимой истории.
- Migration tests verify that the catalog audit table grants runtime `SELECT`/`INSERT`, denies `UPDATE`/`DELETE`, and retains its append-only trigger.

## API errors and audit

- Contract tests assert RFC 9457 media type, stable codes, safe details and correlation identifiers for validation, missing/stale preconditions, absence, authentication, authorization and CSRF.
- Administrative end-to-end tests assert one audit event per committed mutation, no duplicate event on idempotent replay and no object-storage identity in safe diffs.
- Audit assertions execute against PostgreSQL in the same transaction as MockMvc commands; repository mocks are not accepted evidence for transactional atomicity.

## Правила doubles

- Не mock-ать value objects, aggregates и pure domain services.
- Mock допустим для application port, clock, external provider boundary и редкого nondeterministic collaborator.
- PostgreSQL behaviour не заменяется H2 или repository mock; используется Testcontainers.
- Fake adapters должны моделировать success, failure, timeout и duplicate delivery deterministically.
- `@SpringBootTest` не используется там, где достаточно unit или slice test.

## Naming и структура

- Test name описывает observable behaviour и condition.
- Один test проверяет один cohesive outcome, но не обязан иметь один assertion.
- Arrange/Act/Assert разделяются структурой, а не шумными комментариями.
- Test data создаются typed builders/factories с минимальным состоянием.
- Time-dependent test использует injected `Clock`, а не sleep/system clock.
- Concurrency test имеет bounded timeout и доказывает invariant, а не случайный interleaving.

## Coverage

- Overall: line ≥80%, branch ≥70%.
- Critical domain invariants: все известные branches и boundary conditions.
- Coverage exclusion допустим только для generated transport code и trivial application bootstrap.
- Высокий percentage без meaningful assertions не считается завершением feature.

## Flaky tests

- Retry не используется как постоянное лечение flaky test.
- Flaky test блокирует merge, пока причина не устранена или test не помещён в явно ограниченный quarantine с issue, owner и deadline.
- Random/property test сохраняет seed для воспроизведения.
- Containers и ports выделяются динамически; tests не зависят от порядка выполнения.
