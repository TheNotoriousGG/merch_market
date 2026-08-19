# Планы критических inventory-запросов

Статус: acceptance блока 11/12 автоматизирован.

Обновлено: 19 августа 2026 года.

## Representative fixture

`InventoryPerformanceFixture` загружает только в test database:

- 20 000 balance snapshots;
- 40 000 согласованных receipt movements;
- 12 000 reservations: 8 000 active и 4 000 terminal.

Fixture создаётся owner connection, обновляет statistics через `ANALYZE`, очищается после теста и не является Flyway seed. Запросы и `EXPLAIN` выполняются runtime connection с `statement_timeout=2s`.

## Автоматические гарантии

`InventoryQueryPlanAcceptanceTest` выполняет `EXPLAIN (ANALYZE, BUFFERS)` и требует:

- exact variant balance read использует `ix_inventory_balances__variant_warehouse`;
- physical ledger reconciliation использует `ix_inventory_movements__balance_time`;
- bounded due-reservation scan с `FOR UPDATE SKIP LOCKED` использует partial index `ix_inventory_reservations__active_expiry`;
- owner reservation history использует `ix_inventory_reservations__owner_created`;
- ни один из этих paths не выполняет sequential scan целевой большой таблицы;
- каждый representative plan укладывается в локальный ceiling 500 ms, существенно ниже runtime timeout 2 s.

Planner hints и отключение sequential scan не применяются. Повторная оценка необходима при изменении query shape, индекса, распределения данных или production sizing.
