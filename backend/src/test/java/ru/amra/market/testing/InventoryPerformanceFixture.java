package ru.amra.market.testing;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Loads representative inventory volume exclusively for PostgreSQL query-plan acceptance. */
public final class InventoryPerformanceFixture {
    public static final int BALANCE_COUNT = 20_000;
    public static final int RESERVATION_COUNT = 12_000;

    private final JdbcTemplate jdbc;

    public InventoryPerformanceFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Loads balances, a consistent physical ledger and mixed active/terminal reservations. */
    public Profile load() {
        var warehouse = requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class));
        jdbc.update("""
                insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved, version)
                select ?, uuidv7(), 100, 0, 2 from generate_series(1, ?)
                """, warehouse, BALANCE_COUNT);
        jdbc.update("""
                insert into inventory_movements (
                    warehouse_id, variant_id, movement_type, quantity_delta, reason, occurred_at
                )
                select balance.warehouse_id, balance.variant_id, 'RECEIPT', 50,
                       'Representative query-plan fixture',
                       timestamptz '2026-08-01 00:00:00+00' + (movement.ordinal * interval '1 second')
                from inventory_balances balance cross join generate_series(1, 2) movement(ordinal)
                """);
        jdbc.update("""
                insert into inventory_reservations (
                    id, owner_reference, status, expires_at, terminal_at, created_at, updated_at, version
                )
                select uuidv7(), uuidv7(),
                       case when ordinal <= 8000 then 'ACTIVE' else 'RELEASED' end,
                       timestamptz '2026-08-20 00:00:00+00'
                           + ((ordinal % 1440) * interval '1 minute'),
                       case when ordinal <= 8000 then null
                            else timestamptz '2026-08-19 12:00:00+00' end,
                       timestamptz '2026-08-19 00:00:00+00',
                       timestamptz '2026-08-19 12:00:00+00',
                       case when ordinal <= 8000 then 0 else 1 end
                from generate_series(1, ?) ordinal
                """, RESERVATION_COUNT);
        analyze();
        return new Profile(
                warehouse,
                requireNonNull(jdbc.queryForObject(
                        "select variant_id from inventory_balances order by variant_id offset 10000 limit 1",
                        UUID.class)),
                requireNonNull(jdbc.queryForObject(
                        "select owner_reference from inventory_reservations order by id offset 6000 limit 1",
                        UUID.class)));
    }

    /** Clears inventory-owned test rows and restores the seeded primary warehouse. */
    public void clear() {
        jdbc.execute("""
                truncate table
                    pricing_promotion_variants,
                    pricing_promotions,
                    pricing_base_price_periods,
                    inventory_reservation_command_results,
                    inventory_stock_command_results,
                    inventory_audit_events,
                    inventory_command_idempotency,
                    inventory_reservation_events,
                    inventory_reservation_lines,
                    inventory_movements,
                    inventory_reservations,
                    inventory_balances,
                    inventory_job_leases,
                    inventory_warehouses
                """);
        jdbc.update("""
                insert into inventory_warehouses (code, name, status)
                values ('PRIMARY', 'Основной склад', 'ACTIVE')
                """);
        analyze();
    }

    private void analyze() {
        jdbc.execute("analyze inventory_balances");
        jdbc.execute("analyze inventory_movements");
        jdbc.execute("analyze inventory_reservations");
    }

    public record Profile(UUID warehouseId, UUID variantId, UUID ownerReference) {}
}
