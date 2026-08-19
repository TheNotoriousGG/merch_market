package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.amra.market.inventory.application.InventoryIdempotencyConflictException;
import ru.amra.market.inventory.application.ManageInventoryStock;
import ru.amra.market.inventory.application.ReceiveStockCommand;
import ru.amra.market.inventory.application.contract.CreateInventoryReservationRequest;
import ru.amra.market.inventory.application.contract.InventoryReservationOperations;
import ru.amra.market.inventory.application.contract.ReservationRequestLine;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryInvariantViolation;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class InventoryReservationConcurrencyIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private InventoryReservationOperations reservations;

    @Autowired
    private ManageInventoryStock stock;

    @Autowired
    private DataSource dataSource;

    @Test
    void createsAndReplaysReservationWithoutDoubleHoldingStock() {
        var jdbc = jdbc();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "REPLAY");
        receive(warehouse, variant, 5);
        var request = request(UUID.randomUUID(), "reservation-key-0001", warehouse, variant, 2);

        var created = reservations.create(request);
        var replay = reservations.create(request);

        assertThat(replay.id()).isEqualTo(created.id());
        assertThat(balanceReserved(jdbc, warehouse, variant)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory_reservations where id = ?",
                        Integer.class,
                        created.id().value()))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory_reservation_events where reservation_id = ?",
                        Integer.class,
                        created.id().value()))
                .isOne();
    }

    @Test
    void conflictingReplayFailsWithoutChangingBalance() {
        var jdbc = jdbc();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "CONFLICT");
        receive(warehouse, variant, 5);
        var owner = UUID.randomUUID();
        reservations.create(request(owner, "reservation-key-0002", warehouse, variant, 1));

        assertThatThrownBy(() -> reservations.create(request(owner, "reservation-key-0002", warehouse, variant, 2)))
                .isInstanceOf(InventoryIdempotencyConflictException.class);
        assertThat(balanceReserved(jdbc, warehouse, variant)).isOne();
    }

    @Test
    void insufficientSecondLineRollsBackEveryBalanceAndReservationRecord() {
        var jdbc = jdbc();
        var warehouse = warehouse(jdbc);
        var first = activeVariant(jdbc, "MULTI-A");
        var second = activeVariant(jdbc, "MULTI-B");
        receive(warehouse, first, 3);
        receive(warehouse, second, 1);
        var owner = UUID.randomUUID();
        var request = new CreateInventoryReservationRequest(
                owner,
                "reservation-key-0003",
                List.of(
                        new ReservationRequestLine(warehouse.value(), first.value(), 2),
                        new ReservationRequestLine(warehouse.value(), second.value(), 2)));

        assertThatThrownBy(() -> reservations.create(request)).isInstanceOf(InventoryInvariantViolation.class);

        assertThat(balanceReserved(jdbc, warehouse, first)).isZero();
        assertThat(balanceReserved(jdbc, warehouse, second)).isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory_reservations where owner_reference = ?", Integer.class, owner))
                .isZero();
    }

    @Test
    void twoConcurrentRequestsForLastUnitProduceExactlyOneWinner() throws Exception {
        var jdbc = jdbc();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "LAST-UNIT");
        receive(warehouse, variant, 1);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(
                    () -> attempt(request(UUID.randomUUID(), "reservation-key-1001", warehouse, variant, 1), start));
            var second = executor.submit(
                    () -> attempt(request(UUID.randomUUID(), "reservation-key-1002", warehouse, variant, 1), start));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(balanceReserved(jdbc, warehouse, variant)).isOne();
    }

    @Test
    void reversedInputsUseTheSameLockOrderAndBothComplete() throws Exception {
        var jdbc = jdbc();
        var warehouse = warehouse(jdbc);
        var firstVariant = activeVariant(jdbc, "ORDER-A");
        var secondVariant = activeVariant(jdbc, "ORDER-B");
        receive(warehouse, firstVariant, 2);
        receive(warehouse, secondVariant, 2);
        var start = new CountDownLatch(1);
        var first = new ReservationRequestLine(warehouse.value(), firstVariant.value(), 1);
        var second = new ReservationRequestLine(warehouse.value(), secondVariant.value(), 1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var forward = executor.submit(() -> attempt(
                    new CreateInventoryReservationRequest(
                            UUID.randomUUID(), "reservation-order-1001", List.of(first, second)),
                    start));
            var reverse = executor.submit(() -> attempt(
                    new CreateInventoryReservationRequest(
                            UUID.randomUUID(), "reservation-order-1002", List.of(second, first)),
                    start));
            start.countDown();

            assertThat(forward.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(reverse.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(balanceReserved(jdbc, warehouse, firstVariant)).isEqualTo(2);
        assertThat(balanceReserved(jdbc, warehouse, secondVariant)).isEqualTo(2);
    }

    private boolean attempt(CreateInventoryReservationRequest request, CountDownLatch start)
            throws InterruptedException {
        start.await();
        try {
            reservations.create(request);
            return true;
        } catch (InventoryInvariantViolation exception) {
            return false;
        }
    }

    private void receive(WarehouseId warehouse, CatalogVariantId variant, long quantity) {
        stock.receive(new ReceiveStockCommand(
                warehouse, variant, new StockQuantity(quantity), new MovementReason("Reservation fixture"), null));
    }

    private static CreateInventoryReservationRequest request(
            UUID owner, String key, WarehouseId warehouse, CatalogVariantId variant, long quantity) {
        return new CreateInventoryReservationRequest(
                owner, key, List.of(new ReservationRequestLine(warehouse.value(), variant.value(), quantity)));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static WarehouseId warehouse(JdbcTemplate jdbc) {
        return new WarehouseId(requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class)));
    }

    private static long balanceReserved(JdbcTemplate jdbc, WarehouseId warehouse, CatalogVariantId variant) {
        return requireNonNull(jdbc.queryForObject("""
                select reserved from inventory_balances
                where warehouse_id = ? and variant_id = ?
                """, Long.class, warehouse.value(), variant.value()));
    }

    private static CatalogVariantId activeVariant(JdbcTemplate jdbc, String suffix) {
        var categoryId = requireNonNull(jdbc.queryForObject(
                """
                insert into catalog_categories (id, slug, name, display_order, status)
                values (uuidv7(), ?, ?, 0, 'ACTIVE') returning id
                """, UUID.class, "inventory-" + suffix.toLowerCase(Locale.ROOT), "Inventory " + suffix));
        var productId = requireNonNull(jdbc.queryForObject(
                """
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description,
                    status, primary_category_id, published_at
                ) values (uuidv7(), ?, ?, 'fixture', 'fixture', 'ACTIVE', ?, current_timestamp)
                returning id
                """,
                UUID.class,
                "inventory-product-" + suffix.toLowerCase(Locale.ROOT),
                "Product " + suffix,
                categoryId));
        return new CatalogVariantId(
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature
                ) values (uuidv7(), ?, ?, 'Fixture', 'ACTIVE', 0, ?)
                returning id
                """, UUID.class, productId, "INV-" + suffix, "fixture=" + suffix)));
    }
}
