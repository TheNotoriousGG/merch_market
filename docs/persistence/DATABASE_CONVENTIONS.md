# Database conventions

Обновлено: 19 августа 2026 года.

## Ownership и доступ

- Database: `amra_market`.
- Application schema: `amra_shop`.
- `amra_owner` создаётся infrastructure bootstrap и владеет schema.
- `amra_migrator` запускает только Flyway migrations.
- `amra_runtime` используется Hikari pool и не имеет DDL privileges.
- `public` не используется для application objects; `CREATE` для `PUBLIC` отозван.
- Все SQL references в migrations явно привязаны к `amra_shop` либо исполняются с проверенным `search_path`.

## Имена

- Таблицы и колонки: lower-case plural `snake_case`.
- Primary key: `id`; foreign key: `<referenced_singular>_id`.
- Constraints: `pk_<table>`, `fk_<table>__<column>`, `uq_<table>__<columns>`, `ck_<table>__<rule>`.
- Indexes: `ix_<table>__<columns>`; partial/expression purpose отражается suffix.
- Не используются PostgreSQL reserved words, бессодержательные сокращения и quoted identifiers.
- Деньги хранятся как `numeric(19, 2)` вместе с ISO currency, а не как floating point.
- Время хранится в `timestamptz`; Java boundary — `Instant`; DB/session timezone — UTC.

## Общие поля и типы

- Aggregate/entity identifiers: UUIDv7, application-assigned до `persist`.
- Mutable aggregate tables получают `created_at`, `updated_at` и optimistic `version`.
- `created_at` неизменяем; `updated_at` меняется при фактической state mutation.
- Business status моделируется constrained value/status column, не универсальным `deleted` flag.
- JSONB разрешён только для ограниченных, валидируемых noncritical metadata; связи, деньги, SKU, фильтры и invariants остаются typed relational columns.

## Migrations

- Только manual Flyway SQL в `src/main/resources/db/migration`.
- Имя: `V<integer>__<imperative_snake_case_description>.sql`.
- Применённая/merged migration неизменяема; исправление — новая migration.
- DDL задаёт explicit constraint/index names и privileges.
- Новые таблицы создаются ролью `amra_migrator`; default privileges дают runtime только DML.
- Destructive evolution выполняется expand/contract минимум через два релиза.
- Большой backfill — отдельная resumable job с bounded batches, а не долгий migration transaction.
- Concurrent index creation планируется отдельным nontransactional deployment step после измерения и ADR/runbook.

## Query review

- Обычный runtime statement timeout: 2 секунды; admin reports — отдельный профиль до 10 секунд.
- Индекс добавляется под подтверждённый constraint/query, а не «на будущее».
- Critical query принимается после `EXPLAIN (ANALYZE, BUFFERS)` на realistic distribution/volume.
- Pagination по большим mutable feeds использует keyset; unbounded result sets запрещены.
- ORM associations default to lazy/bounded access; repositories используют explicit fetch/projection и N+1 tests.
