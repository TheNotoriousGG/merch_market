# ADR-0001: Модульный монолит как начальная архитектура

- Статус: принято
- Дата: 19 августа 2026 года
- Владельцы решения: владелец продукта и backend architecture

## Контекст

«Амра Шоп» начинает с единого торгового backend с ожидаемой начальной нагрузкой до 50 RPS peak. Система должна быть production-ready, горизонтально масштабируемой, хорошо тестируемой и допускающей позднее выделение отдельных capabilities без преждевременной распределённой сложности.

Каталог, остатки, цены и заказы имеют тесные consistency requirements. В частности, checkout должен атомарно создавать reservation и order state без oversell. Разделение этих операций по independent services на старте добавило бы network failure modes, distributed transactions, schema/event compatibility и operational burden без подтверждённой выгоды.

## Решение

Начать с одного deployable Spring Boot application и одного Gradle application module, организованного как модульный монолит:

- business modules: `catalog`, `inventory`, `customer`, `identity-access`, `cart`, `pricing`, `ordering`, `administration`, `outbox`;
- package structure модуля: `api`, `application`, `domain`, `infrastructure`;
- module boundaries и cycles проверяются Spring Modulith и ArchUnit;
- domain packages не зависят от Spring, JPA и generated transport types;
- module data ownership явный; direct repository access и cross-module JPA navigation запрещены;
- modules взаимодействуют через published application contracts и domain events;
- cross-module ACID transaction допускается только в explicitly orchestrated critical use case;
- post-commit side effects доставляются через transactional outbox;
- application instances не полагаются на local mutable state.

## Рассмотренные альтернативы

### Микросервисы с первого дня

Отклонено: нагрузка и team topology не требуют independent deployments, а distributed failure modes ухудшают delivery speed и correctness критических order/inventory scenarios.

### Неразделённый layered monolith

Отклонено: технические слои без domain module ownership упрощают обход boundaries, создают shared model и затрудняют безопасное выделение capabilities.

### WebFlux/R2DBC application

Отклонено отдельным решением: blocking PostgreSQL/JPA ecosystem и Java 25 virtual threads делают Spring MVC более прямым и поддерживаемым выбором для текущего профиля.

## Последствия

Положительные:

- простые local transactions для strict invariants;
- один deployable artifact и единый operational surface;
- быстрые integration tests с реальным PostgreSQL;
- enforceable domain boundaries и понятные extraction seams;
- меньше network/protocol/schema coordination на старте.

Отрицательные и риски:

- ошибка module boundaries может превратить систему в tightly coupled monolith;
- один deployment связывает release cadence модулей;
- тяжёлый module способен влиять на process-wide resources;
- cross-module transactions могут стать скрытым coupling.

Меры контроля:

- architecture tests blocking в CI;
- явный data ownership и запрет repository bypass;
- bounded concurrency/resource pools;
- module-level metrics и traces;
- ADR для каждой новой cross-module dependency;
- extraction рассматривается только после измерения coupling, load или release friction.

## Критерии пересмотра

Решение пересматривается, если одновременно присутствует доказанная причина и operational readiness, например:

- capability требует independent scaling, которое невозможно разумно обеспечить внутри process;
- разные команды нуждаются в independent release cadence и ownership;
- isolation requirement нельзя обеспечить module/resource boundaries;
- measured failure blast radius неприемлем;
- regulatory/data-placement requirement требует физического разделения.

Сам рост количества строк кода или таблиц не является основанием для микросервисов.

## Проверка соблюдения

- Spring Modulith verification test;
- ArchUnit rules для package dependencies и cycles;
- integration tests public module contracts;
- review database ownership и cross-module foreign keys;
- CI запрещает merge при нарушении boundary rules.
