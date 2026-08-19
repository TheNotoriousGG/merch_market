package ru.amra.market.inventory.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.testing.InventoryPerformanceFixture;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class InventoryQueryPlanAcceptanceTest extends PostgreSqlIntegrationTest {
    @Autowired
    private JdbcTemplate runtimeJdbc;

    @Autowired
    private Flyway flyway;

    private InventoryPerformanceFixture fixture;
    private InventoryPerformanceFixture.Profile profile;

    @BeforeEach
    void loadRepresentativeInventory() {
        fixture = new InventoryPerformanceFixture(
                new JdbcTemplate(flyway.getConfiguration().getDataSource()));
        fixture.clear();
        profile = fixture.load();
    }

    @AfterEach
    void removeRepresentativeInventory() {
        fixture.clear();
    }

    @Test
    void keepsCriticalInventoryReadsIndexedAndBoundedAtRepresentativeVolume() {
        assertThat(runtimeJdbc.queryForObject("select count(*) from inventory_balances", Integer.class))
                .isEqualTo(InventoryPerformanceFixture.BALANCE_COUNT);
        assertThat(runtimeJdbc.queryForObject("select count(*) from inventory_movements", Integer.class))
                .isEqualTo(InventoryPerformanceFixture.BALANCE_COUNT * 2);
        assertThat(runtimeJdbc.queryForObject("select count(*) from inventory_reservations", Integer.class))
                .isEqualTo(InventoryPerformanceFixture.RESERVATION_COUNT);
        assertThat(runtimeJdbc.queryForObject("select current_setting('statement_timeout')", String.class))
                .isEqualTo("2s");

        assertThat(plan("""
                        select warehouse.code, balance.on_hand, balance.reserved, balance.version
                        from inventory_balances balance
                        join inventory_warehouses warehouse on warehouse.id = balance.warehouse_id
                        where warehouse.code = 'PRIMARY' and balance.variant_id = '%s'
                        """.formatted(profile.variantId())))
                .contains("ix_inventory_balances__variant_warehouse")
                .doesNotContain("Seq Scan on inventory_balances");
        assertThat(plan("""
                        select coalesce(sum(quantity_delta), 0)
                        from inventory_movements
                        where warehouse_id = '%s' and variant_id = '%s'
                        """.formatted(profile.warehouseId(), profile.variantId())))
                .contains("ix_inventory_movements__balance_time")
                .doesNotContain("Seq Scan on inventory_movements");
        assertThat(plan("""
                        select id from inventory_reservations
                        where status = 'ACTIVE'
                          and expires_at <= timestamptz '2026-08-20 12:00:00+00'
                        order by expires_at, id limit 100
                        for update skip locked
                        """))
                .contains("ix_inventory_reservations__active_expiry")
                .doesNotContain("Seq Scan on inventory_reservations");
        assertThat(plan("""
                        select id from inventory_reservations
                        where owner_reference = '%s'
                        order by created_at desc, id desc limit 20
                        """.formatted(profile.ownerReference())))
                .contains("ix_inventory_reservations__owner_created")
                .doesNotContain("Seq Scan on inventory_reservations");
    }

    private String plan(String sql) {
        var result = String.join(
                "\n",
                runtimeJdbc.query(
                        "explain (analyze, buffers, costs, summary) " + sql, (row, rowNumber) -> row.getString(1)));
        assertThat(executionTimeMillis(result)).isLessThan(500.0);
        return result;
    }

    private static double executionTimeMillis(String plan) {
        var marker = "Execution Time: ";
        var start = plan.lastIndexOf(marker);
        var end = plan.indexOf(" ms", start);
        return Double.parseDouble(plan.substring(start + marker.length(), end));
    }
}
