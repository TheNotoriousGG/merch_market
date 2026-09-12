package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.inventory.application.ExpireInventoryReservations;
import ru.amra.market.inventory.application.ManageInventoryStock;
import ru.amra.market.inventory.application.ReceiveStockCommand;
import ru.amra.market.inventory.application.contract.CreateInventoryReservationRequest;
import ru.amra.market.inventory.application.contract.InventoryReservationOperations;
import ru.amra.market.inventory.application.contract.ReservationCommandRequest;
import ru.amra.market.inventory.application.contract.ReservationRequestLine;
import ru.amra.market.inventory.application.port.InventoryJobLease;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryInvariantViolation;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.ReservationStatus;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class InventoryExpiryWorkerIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private ExpireInventoryReservations expiry;

    @Autowired
    private InventoryReservationOperations reservations;

    @Autowired
    private InventoryJobLease leases;

    @Autowired
    private ManageInventoryStock stock;

    @Autowired
    private DataSource dataSource;

    @Test
    void expiresBoundedBatchesAndRepeatedRunHasNoSideEffects() {
        var jdbc = jdbc();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "EXPIRY-BATCH");
        receive(warehouse, variant, 10);
        var created = java.util.stream.IntStream.range(0, 3)
                .mapToObj(index -> reservations.create(new CreateInventoryReservationRequest(
                        UUID.randomUUID(),
                        "expiry-create-000" + index,
                        List.of(new ReservationRequestLine(warehouse.value(), variant.value(), 2)))))
                .toList();
        created.forEach(reservation -> age(jdbc, reservation.id().value()));

        assertThat(expiry.runBatch("expiry-worker-a", Duration.ofSeconds(30), 2))
                .isEqualTo(2);
        assertThat(expiry.runBatch("expiry-worker-a", Duration.ofSeconds(30), 2))
                .isOne();
        assertThat(expiry.runBatch("expiry-worker-a", Duration.ofSeconds(30), 2))
                .isZero();

        created.forEach(reservation -> {
            assertThat(reservations
                            .get(
                                    reservation.ownerReference().value(),
                                    reservation.id().value())
                            .status())
                    .isEqualTo(ReservationStatus.EXPIRED);
            assertThat(jdbc.queryForObject(
                            "select count(*) from inventory_reservation_events where reservation_id = ?",
                            Integer.class,
                            reservation.id().value()))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                            "select count(*) from inventory_reservation_command_results where reservation_id = ?",
                            Integer.class,
                            reservation.id().value()))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                            "select count(*) from inventory_movements where reservation_id = ?",
                            Integer.class,
                            reservation.id().value()))
                    .isZero();
        });
        assertThat(jdbc.queryForObject(
                        "select reserved from inventory_balances where warehouse_id = ? and variant_id = ?",
                        Long.class,
                        warehouse.value(),
                        variant.value()))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select on_hand from inventory_balances where warehouse_id = ? and variant_id = ?",
                        Long.class,
                        warehouse.value(),
                        variant.value()))
                .isEqualTo(10);
        assertReservedMatchesActiveLines(jdbc, warehouse, variant);
    }

    @Test
    void expiryAndCommitRaceProduceOneExpiredOutcomeWithoutStockDrift() throws Exception {
        var jdbc = jdbc();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "EXPIRY-COMMIT-RACE");
        var owner = UUID.randomUUID();
        receive(warehouse, variant, 5);
        var created = reservations.create(new CreateInventoryReservationRequest(
                owner,
                "expiry-race-create-0001",
                List.of(new ReservationRequestLine(warehouse.value(), variant.value(), 3))));
        age(jdbc, created.id().value());
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var expiration = executor.submit(() -> {
                start.await();
                return expiry.runBatch("expiry-worker-a", Duration.ofSeconds(30), 100) == 1;
            });
            var commit = executor.submit(() -> {
                start.await();
                try {
                    reservations.commit(
                            new ReservationCommandRequest(owner, created.id().value(), "expiry-race-commit-0001"));
                    return true;
                } catch (InventoryInvariantViolation exception) {
                    return false;
                }
            });
            start.countDown();

            assertThat(List.of(expiration.get(5, TimeUnit.SECONDS), commit.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(reservations.get(owner, created.id().value()).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory_movements where reservation_id = ?",
                        Integer.class,
                        created.id().value()))
                .isZero();
        assertThat(jdbc.queryForMap(
                        "select on_hand, reserved from inventory_balances where warehouse_id = ? and variant_id = ?",
                        warehouse.value(),
                        variant.value()))
                .containsEntry("on_hand", 5L)
                .containsEntry("reserved", 0L);
        assertReservedMatchesActiveLines(jdbc, warehouse, variant);
    }

    @Test
    void leaseRejectsCompetingOwnerAndAllowsExpiredTakeover() {
        var jdbc = jdbc();
        var job = "test-expiry-" + UUID.randomUUID().toString().substring(0, 8);

        assertThat(leases.tryAcquire(job, "instance-a", Duration.ofSeconds(30))).isTrue();
        assertThat(leases.tryAcquire(job, "instance-b", Duration.ofSeconds(30))).isFalse();
        assertThat(leases.tryAcquire(job, "instance-a", Duration.ofSeconds(30))).isTrue();

        jdbc.update(
                String.join(
                        " ",
                        "update inventory_job_leases",
                        "set leased_until = clock_timestamp() - interval '1 second'",
                        "where job_name = ?"),
                job);
        assertThat(leases.tryAcquire(job, "instance-b", Duration.ofSeconds(30))).isTrue();
        assertThat(jdbc.queryForObject(
                        "select owner_instance_id from inventory_job_leases where job_name = ?", String.class, job))
                .isEqualTo("instance-b");
    }

    private void receive(WarehouseId warehouse, CatalogVariantId variant, long quantity) {
        stock.receive(new ReceiveStockCommand(
                warehouse, variant, new StockQuantity(quantity), new MovementReason("Expiry fixture"), null));
    }

    private static void age(JdbcTemplate jdbc, UUID reservationId) {
        jdbc.update("""
                update inventory_reservations
                set created_at = clock_timestamp() - interval '30 minutes',
                    expires_at = clock_timestamp() - interval '1 second'
                where id = ?
                """, reservationId);
    }

    private static void assertReservedMatchesActiveLines(
            JdbcTemplate jdbc, WarehouseId warehouse, CatalogVariantId variant) {
        assertThat(jdbc.queryForObject("""
                        select balance.reserved = coalesce(sum(line.quantity)
                            filter (where reservation.status = 'ACTIVE'), 0)
                        from inventory_balances balance
                        left join inventory_reservation_lines line
                          on line.warehouse_id = balance.warehouse_id
                         and line.variant_id = balance.variant_id
                        left join inventory_reservations reservation on reservation.id = line.reservation_id
                        where balance.warehouse_id = ? and balance.variant_id = ?
                        group by balance.reserved
                        """, Boolean.class, warehouse.value(), variant.value()))
                .isTrue();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static WarehouseId warehouse(JdbcTemplate jdbc) {
        return new WarehouseId(requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class)));
    }

    private static CatalogVariantId activeVariant(JdbcTemplate jdbc, String suffix) {
        var normalized = suffix.toLowerCase(Locale.ROOT);
        var category = requireNonNull(jdbc.queryForObject("""
                insert into catalog_categories (id, slug, name, display_order, status)
                values (uuidv7(), ?, ?, 0, 'ACTIVE') returning id
                """, UUID.class, "expiry-" + normalized, "Expiry " + suffix));
        var product = requireNonNull(
                jdbc.queryForObject("""
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description,
                    status, primary_category_id, published_at
                ) values (uuidv7(), ?, ?, 'fixture', 'fixture', 'ACTIVE', ?, current_timestamp)
                returning id
                """, UUID.class, "expiry-product-" + normalized, "Product " + suffix, category));
        return new CatalogVariantId(
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature
                ) values (uuidv7(), ?, ?, 'Fixture', 'ACTIVE', 0, ?)
                returning id
                """, UUID.class, product, "EXP-" + suffix, "fixture=" + suffix)));
    }
}
