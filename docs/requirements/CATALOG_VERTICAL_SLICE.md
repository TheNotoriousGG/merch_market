# Техническое задание: вертикальный срез каталога

Статус: зафиксированная исходная спецификация этапа 7.

Дата фиксации: 19 августа 2026 года.

Документ является источником продуктового контекста для реализации каталога между сессиями. Изменение scope, инвариантов или критериев приёмки требует явного согласования с владельцем проекта и обновления этого файла до изменения кода.

## 1. Цель

Создать production-ready backend-каталог «Амра Шоп», который заменит демонстрационные товарные массивы frontend и обеспечит полный путь чтения и администрирования каталога:

`OpenAPI → HTTP adapter → application use case → domain → PostgreSQL`.

После завершения этапа покупатель должен получать опубликованные категории и товары из backend, открывать компактную карточку и подробности товара. Сотрудник с нужными правами должен управлять структурой каталога без прямого доступа к базе данных.

## 2. Зафиксированный продуктовый контекст

Каталог продолжает уже утверждённое поведение frontend:

- категории выбираются из центрального мегабара и раскрывающейся панели;
- начальные пункты мегабара: «Одежда», «Аксессуары», «Сумки», «Брюки»;
- переход ведёт на отдельный маршрут каталога, а не на блок главной страницы;
- список содержит компактные карточки;
- клик по карточке открывает только выбранный товар с подробным описанием;
- действия избранного и корзины не должны открывать карточку;
- количество карточек адаптирует frontend, backend возвращает обычную страничную выдачу;
- используется только пагинация — параллельной кнопки «Показать ещё» нет;
- внешний вид хедера на внутренних страницах не меняется;
- T‑Shop используется только как ориентир по полноте данных и удобству, без копирования дизайна или структуры один в один.

Текущие frontend fixtures («Футболка „Серия 01“», «Худи свободного кроя», «Сумка Soft Box», «Часы Amra Mono» и другие) являются демонстрационным набором для acceptance tests, а не production-данными Flyway migration.

## 3. Scope этапа 7

### 3.1. Входит в этап

- дерево категорий и подкатегорий;
- товары и их жизненный цикл;
- варианты товара по цвету, размеру и другим SKU-образующим признакам;
- immutable business SKU;
- typed filterable attributes;
- описания, состав/материал, посадка и другие характеристики карточки;
- коллекции и принадлежность товара к коллекциям;
- media metadata, порядок изображений, alt text и object-storage port;
- публичный список опубликованных товаров;
- публичная подробная карточка товара;
- поиск PostgreSQL FTS + `pg_trgm`;
- typed filters, sorting allowlist и page-based pagination с totals;
- admin commands создания, редактирования, публикации и архивации;
- optimistic locking, ETag и `If-Match` для конкурентного редактирования;
- изменение slug с сохранением безопасного alias/redirect;
- audit event на административные изменения;
- OpenAPI contract, generated Java transport interfaces/DTO и TypeScript client;
- Flyway schema, constraints, indexes и тесты на PostgreSQL 18.4.

### 3.2. Не входит в этап

- остатки и доступность SKU — этап 8 Inventory;
- избранное, корзина и сохранение выбранного варианта — этап 9;
- расчёт базовой/акционной цены, Sale и сортировка по фактической цене — этап 10;
- checkout, доставка, возвраты и оплата — последующие этапы;
- CMS рекламного слайдера, футера и произвольных страниц;
- production object-storage/CDN provider: на этом этапе создаются port, metadata и controllable test adapter;
- Redis, OpenSearch, broker и отдельный catalog microservice;
- мобильное представление — отдельный frontend-этап.

До соответствующих этапов frontend может сохранять демонстрационные price/cart/favorite данные, но идентификаторы товара и варианта должен брать из catalog contract после интеграции.

## 4. Пользователи и права

### Покупатель или гость

- видит только товары и категории в публичном состоянии;
- ищет, фильтрует, сортирует и листает страницы;
- открывает подробности по canonical slug;
- не получает internal notes, draft data, object keys, audit metadata и технические поля хранения.

### Catalog manager

- создаёт и изменяет категории, товары, варианты, attributes и media metadata;
- публикует и архивирует товары при выполнении инвариантов;
- меняет порядок категорий, вариантов и изображений;
- не изменяет остатки, заказы, клиентов и pricing rules.

### Administrator

- имеет те же catalog capabilities в рамках административного API;
- не обходит application use cases и audit.

Catalog mutation требует `CATALOG_MANAGER` либо `ADMIN` и подтверждённого MFA claim. Все команды размещаются под `/api/v1/admin/**`, используют отдельные admin DTO и не доступны `CUSTOMER`, `SUPPORT`, `ORDER_MANAGER` или `WAREHOUSE_MANAGER` без catalog permission.

## 5. Доменная модель

### Category

- UUIDv7 `id`;
- immutable identity и editable display name;
- canonical `slug`;
- nullable `parentId` по adjacency-list модели;
- `displayOrder` среди siblings;
- status `ACTIVE` или `HIDDEN`;
- timestamps и optimistic `version`.

Category занимает одно место в domain tree. Пункт мегабара может ссылаться на любой category slug: «Брюки» ссылается на соответствующую ветку/подкатегорию, а не создаёт дубликат категории «Брюки и шорты» внутри «Одежды».

### Product

- UUIDv7 `id`;
- canonical `slug` и history aliases;
- name, short description и full description;
- lifecycle `DRAFT`, `ACTIVE`, `ARCHIVED`;
- primary category и дополнительные category assignments;
- zero or more collection assignments;
- material/composition, fit и безопасный набор display characteristics;
- ordered media metadata;
- publication timestamp, audit timestamps и optimistic `version`.

### ProductVariant

- UUIDv7 `id`;
- immutable normalized SKU, например `AMR-TS01-BLK-M`;
- variant status `ACTIVE` или `ARCHIVED`;
- ordered typed attribute values, например `color=GRAPHITE`, `size=M`;
- display label и display order;
- вариант принадлежит ровно одному Product.

Отсутствие stock record на этом этапе не означает наличие товара. Availability добавляется только Inventory-модулем.

### AttributeDefinition и AttributeValue

- типы MVP: `TEXT`, `COLOR`, `SIZE`, `DIMENSION`;
- definition определяет code, display name, filterability, variant-defining flag и display order;
- value валидируется по типу definition;
- filterable и SKU-critical значения хранятся реляционно;
- произвольный невалидируемый JSON не используется для размеров, цветов, SKU и фильтров.

### Collection

- UUIDv7, canonical slug, name, description, status и ordering;
- ordered many-to-many membership товаров;
- публичная коллекция содержит только `ACTIVE` products.

«Новинки» строятся по `publishedAt` в порядке убывания за последние 30 дней относительно injected `Clock`, без permanent `isNew` flag. «Товары недели» и рекламные кампании являются отдельным editorial-placement concern и не должны маскироваться product attribute. Их write model будет уточнена вместе с administration/content scope; catalog на этом этапе предоставляет стабильные product references.

### ProductMedia

- UUIDv7 `id`, product/optional variant owner;
- media type, object key, content type, width, height, alt text и display order;
- один primary image на product presentation;
- object key не возвращается публичному клиенту;
- public delivery URL формирует media adapter;
- alt text обязателен перед публикацией видимого изображения.

## 6. Доменное поведение и инварианты

### Категории

- sibling slugs уникальны без учёта регистра;
- категория не может быть собственным parent или образовывать cycle;
- удаление категории с descendants или assigned products запрещено;
- скрытая категория не появляется в публичном дереве, но не удаляет товары;
- порядок siblings детерминирован: `displayOrder`, затем stable id tie-breaker;
- глубина дерева ограничивается application policy и проверяется до записи; начальный максимум — 5 уровней.

### Товары

- новый товар создаётся только в `DRAFT`;
- допустимые переходы: `DRAFT → ACTIVE → ARCHIVED`; `ARCHIVED` является terminal state;
- `ACTIVE` требует name, canonical slug, short/full description, primary active category, минимум один active variant и primary media с alt text;
- Product без вариантов не публикуется;
- `ARCHIVED` не появляется в публичной выдаче, но сохраняется для ссылочной целостности будущих заказов;
- hard delete опубликованного товара запрещён;
- изменение business content увеличивает optimistic version.

### SKU и варианты

- SKU нормализуется в uppercase ASCII и сравнивается case-insensitive;
- SKU уникален глобально и после создания не меняется;
- изменение SKU создаёт новый variant, старый архивируется;
- комбинация variant-defining values уникальна внутри product;
- вариант нельзя физически удалить после появления внешней ссылки; используется archive;
- display order не определяет identity.

### Slug и aliases

- canonical slug уникален в публичном product namespace;
- предыдущий slug сохраняется как alias с permanent redirect semantics;
- alias нельзя повторно назначить другому товару;
- alias chains и cycles запрещены; lookup сразу разрешается в canonical product;
- slug не содержит PII и нормализуется предсказуемо.

## 7. Публичные сценарии API

Canonical paths уточняются и утверждаются непосредственно в OpenAPI до implementation, но обязательные capabilities следующие:

### Дерево категорий

`GET /api/v1/catalog/categories`

- возвращает только visible navigation tree;
- содержит id, slug, name, hierarchy, order и доступные child nodes;
- подходит для мегабара и панели подкатегорий;
- response детерминирован и не требует N+1 queries.

### Список товаров

`GET /api/v1/catalog/products`

- page pagination с `page`, `size`, totals и total pages;
- default size `24`, maximum size `60`;
- фильтры: category slug с descendants, collection slug, `new`, typed size/color values;
- search query `q`: trimmed, 2–100 characters;
- sorting allowlist этапа: `MANUAL`, `NEWEST`, `NAME_ASC`;
- неизвестный filter/sort value возвращает 400 Problem Details, а не молча игнорируется;
- пустой результат возвращает 200 с пустым items и корректной metadata;
- в выдачу попадают только `ACTIVE` products с active presentation data.

Price fields и `PRICE_ASC` добавляются pricing-модулем contract-first, а stock/available sizes — inventory-модулем. До этого catalog не выдумывает цену или наличие.

### Подробная карточка

`GET /api/v1/catalog/products/{slug}`

- возвращает canonical product, descriptions, categories, collections, characteristics, ordered media и active variants;
- alias slug возвращает redirect на canonical URL, а не duplicate body;
- неизвестный или непубличный product возвращает 404 без раскрытия его статуса;
- response покрывает catalog-owned часть существующего подробного модального окна; pricing и availability дополняют representation на своих этапах;
- `ETag` публичного representation меняется при изменении видимого content.

### Поиск

- индексируются name, descriptions, SKU-safe public terms и разрешённые attribute labels;
- используется русская/простая нормализация PostgreSQL FTS и `pg_trgm` для typo tolerance;
- draft/archived products никогда не попадают в search document;
- ranking детерминирован и имеет stable tie-breaker;
- поиск не допускает generic query language или передачу SQL fragments.

## 8. Административные сценарии API

- создать/изменить/скрыть категорию;
- переместить категорию с cycle validation;
- создать draft product;
- изменить content, assignments и characteristics;
- создать/архивировать variant;
- назначить media metadata и primary image;
- опубликовать product с полной validation report;
- архивировать product;
- изменить canonical slug с alias;
- управлять collection membership и order.

Create command возвращает 201 и resource location. Update/archive/publish commands требуют `If-Match`; отсутствующий precondition возвращает 428, stale ETag — 412. Validation conflict возвращает RFC 9457 Problem Details со stable code и violations. Повторная одинаковая команда не должна создавать duplicate variant, alias или assignment.

Минимальные stable error codes:

- `CATEGORY_NOT_FOUND`;
- `CATEGORY_CYCLE`;
- `CATEGORY_NOT_EMPTY`;
- `PRODUCT_NOT_FOUND`;
- `PRODUCT_NOT_PUBLISHABLE`;
- `PRODUCT_SLUG_CONFLICT`;
- `SKU_CONFLICT`;
- `VARIANT_COMBINATION_CONFLICT`;
- `PRECONDITION_REQUIRED`;
- `STALE_RESOURCE_VERSION`;
- `INVALID_CATALOG_FILTER`.

## 9. Persistence requirements

- таблицы модуля используют plural `snake_case` в schema `amra_shop`;
- JPA entities пишутся вручную и не генерируются из OpenAPI;
- module owns its tables; cross-module JPA relations запрещены;
- foreign keys, uniqueness, checks и indexes получают explicit meaningful names;
- category hierarchy использует adjacency list и recursive CTE;
- case-insensitive uniqueness SKU/slug обеспечивается нормализованной колонкой или expression index;
- search document/index обновляется transactionally с catalog mutation;
- списковые queries используют projection/fetch plan без OSIV;
- migration создаёт схему с нуля и проверяется runtime role;
- destructive schema evolution следует expand/contract;
- critical list/detail/search queries получают `EXPLAIN (ANALYZE, BUFFERS)` review на realistic fixture volume до production readiness.

Начальная проверочная выборка: не менее 10 000 products, 40 000 variants, дерево до 5 уровней и неравномерное распределение категорий. Это performance fixture, а не production seed.

## 10. Нефункциональные требования

- публичные read operations stateless относительно application instance;
- никакого instance-local cache в correctness path;
- обычный DB statement timeout — 2 секунды;
- page size и parallel database work bounded;
- список и detail не выполняют N+1 queries;
- публичные responses не содержат PII, internal notes, object keys и audit actor data;
- logs не содержат full search payload при появлении риска PII;
- API backward-compatible внутри `/api/v1`;
- generated code вручную не редактируется;
- all timestamps — UTC `Instant`;
- source of truth для transport contract — modular OpenAPI 3.0.3.

## 11. Тестовая матрица

### Unit/domain

- category cycle/self-parent/depth;
- lifecycle transitions и publish completeness;
- SKU normalization/immutability;
- unique variant combination;
- slug alias reuse/chain/cycle;
- deterministic ordering.

### Persistence/integration

- fresh Flyway migration и runtime permissions;
- recursive category tree;
- database uniqueness/check/FK enforcement;
- search visibility/ranking/typo scenarios;
- pagination totals and stable ordering;
- no N+1 for list/detail;
- stale concurrent update;
- query plans на representative fixture.

### Web/contract/security

- generated DTO соответствует actual JSON;
- anonymous public read succeeds;
- draft/archived data is indistinguishable from missing;
- catalog manager/admin MFA boundaries;
- missing/stale `If-Match` gives 428/412;
- validation uses RFC 9457 and stable codes;
- unsupported filters/sorts fail closed;
- OpenAPI compatibility and TypeScript client generation remain green.

Critical domain invariants require 100% scenario coverage and mutation testing. Overall project thresholds remain at least 80% line and 70% branch.

## 12. Acceptance scenarios

1. Catalog manager creates «Одежда» and its subcategories without direct SQL.
2. Manager creates draft «Футболка „Серия 01“», variants `S/M/L/XL`, colors, material, descriptions and primary media.
3. Publication fails while required data is incomplete and succeeds after correction.
4. Guest sees the product in category, search and «Новинки», opens its detailed representation and receives ordered variants/media.
5. Guest cannot retrieve a draft or archived product by id, slug, alias, search or category listing.
6. Changing the product slug preserves the old URL as a direct canonical redirect without alias chain.
7. Two managers editing the same version cannot silently overwrite each other.
8. A request with forged role, missing MFA or insufficient permission cannot mutate catalog data.
9. Pagination имеет детерминированный tie-breaker, корректные totals для каждого запроса и никогда не совмещается с семантикой «Показать ещё».
10. Frontend can replace hardcoded category/product fixtures using the generated TypeScript client without accessing database concepts.

## 13. Definition of Done

- OpenAPI contract and domain invariants reviewed before implementation;
- domain, application ports/use cases, HTTP and PostgreSQL adapters preserve module boundaries;
- migrations, indexes and constraints reviewed;
- all tests in the matrix that apply to the slice are green;
- `clean qualityGate` and catalog mutation gate pass;
- generated Java/TypeScript drift absent;
- container smoke test remains green;
- Markdown context, ADR/decision register and project status updated;
- logical Conventional Commits exist in `feature/catalog`;
- merge into `main` is fast-forward only after the complete catalog gate closes.
