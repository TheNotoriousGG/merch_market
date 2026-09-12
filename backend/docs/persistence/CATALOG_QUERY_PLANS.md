# Catalog query-plan acceptance

Обновлено: 19 августа 2026 года.

Статус: автоматизированный baseline этапа 7; не заменяет production load/soak и повторный review на фактическом распределении данных.

## Representative fixture

`CatalogQueryPlanAcceptanceTest` создаёт данные только внутри PostgreSQL integration environment и полностью удаляет их после проверки. Ни одна performance-запись не входит во Flyway или production seed.

Baseline содержит:

- 10 000 опубликованных товаров;
- 40 000 активных вариантов — ровно четыре на товар;
- 10 000 primary media;
- два variant-defining значения на каждый вариант и одну характеристику товара;
- дерево категорий глубиной пять уровней;
- неравномерное распределение товаров `70% / 20% / 10%`;
- активную редакционную коллекцию из 2 000 товаров;
- 1% селективную search-группу для совместной проверки FTS и trigram.

После загрузки migrator выполняет `ANALYZE`. Сами catalog reads выполняются runtime connection с production-equivalent `statement_timeout = 2s`.

## Зафиксированные планы

Тест выполняет `EXPLAIN (ANALYZE, BUFFERS, COSTS, SUMMARY)` и проверяет:

- newest page использует partial `ix_catalog_products__active_newest` и индекс primary media;
- canonical detail lookup использует выражение `lower(canonical_slug)` и `uq_catalog_products__canonical_slug_ci`, без sequential scan;
- редкая category-ветка использует bounded hash semi-join и укладывается в 250 ms;
- FTS + trigram search сохраняет top-N plan и укладывается в 500 ms;
- реальные list reader paths возвращают корректные totals и 24 карточки с двумя группами variant options;
- category, collection и search paths выполняются под обычным двухсекундным DB timeout.

На объёме 10 000 строк PostgreSQL может обоснованно выбрать sequential scan для широкого category count или объединённого `FTS OR trigram`: чтение компактной таблицы дешевле bitmap/GIN startup. Тест не отключает `enable_seqscan` и не принуждает planner hint-ами. Для селективного canonical lookup и ordered newest path использование индекса обязательно.

## Найденное улучшение

Spring Data derived `IgnoreCase` не гарантирует выражение, совпадающее с PostgreSQL index на `lower(canonical_slug)`. Repository query теперь задаёт `lower(...)` явно, а acceptance plan доказывает применение индексного access path.

## Rebaseline policy

Планы пересматриваются при изменении SQL, индексов, PostgreSQL major, статистики или ожидаемого объёма. Перед production readiness baseline повторяется на production-like hardware и распределении; этап 15 отдельно добавляет concurrent load/soak относительно 50 RPS и фиксирует percentile latency, saturation и connection-pool headroom.
