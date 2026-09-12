package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import ru.amra.market.inventory.application.InventoryIdempotencyConflictException;
import ru.amra.market.inventory.application.ManageInventoryStock;
import ru.amra.market.inventory.application.ReceiveStockCommand;
import ru.amra.market.inventory.application.ReservationNotFoundException;
import ru.amra.market.inventory.application.contract.CreateInventoryReservationRequest;
import ru.amra.market.inventory.application.contract.InventoryReservationOperations;
import ru.amra.market.inventory.application.contract.ReservationCommandRequest;
import ru.amra.market.inventory.application.contract.ReservationRequestLine;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryInvariant;
import ru.amra.market.inventory.domain.InventoryInvariantViolation;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.ReservationStatus;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class InventoryReservationLifecycleIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private InventoryReservationOperations reservations;

    @Autowired
    private ManageInventoryStock stock;

    @Autowired
    private DataSource dataSource;

    @Test
    void extendsExactlyOnceAndReplaysOriginalSnapshotsAfterCommit() {
        var jdbc = jdbc();
        var owner = UUID.randomUUID();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "EXTEND-COMMIT");
        receive(warehouse, variant, 8);
        var createRequest = createRequest(owner, "create-lifecycle-0001", warehouse, variant, 3);
        var created = reservations.create(createRequest);

        var extendRequest = command(owner, created.id().value(), "extend-lifecycle-0001");
        var extended = reservations.extend(extendRequest);
        assertThat(extended.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(extended.version()).isOne();
        assertThat(extended.extensionCount()).isOne();
        assertThat(Duration.between(created.expiresAt(), extended.expiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThatThrownBy(
                        () -> reservations.extend(command(owner, created.id().value(), "extend-lifecycle-other-0001")))
                .isInstanceOf(InventoryInvariantViolation.class)
                .extracting(error -> ((InventoryInvariantViolation) error).invariant())
                .isEqualTo(InventoryInvariant.RESERVATION_EXTENSION_LIMIT_REACHED);

        var committed = reservations.commit(command(owner, created.id().value(), "commit-lifecycle-0001"));
        assertThat(committed.status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(committed.version()).isEqualTo(2);
        assertBalance(jdbc, warehouse, variant, 5, 0);
        assertThat(movementCount(jdbc, created.id().value())).isOne();

        var replayedExtension = reservations.extend(extendRequest);
        var replayedCreation = reservations.create(createRequest);
        var replayedCommit = reservations.commit(command(owner, created.id().value(), "commit-lifecycle-0001"));
        assertThat(replayedExtension.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(replayedExtension.version()).isOne();
        assertThat(replayedCreation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(replayedCreation.version()).isZero();
        assertThat(replayedCommit.status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(replayedCommit.version()).isEqualTo(2);
        assertThat(movementCount(jdbc, created.id().value())).isOne();
    }

    @Test
    void releasesAllLinesWithoutPhysicalMovementAndExactlyReplays() {
        var jdbc = jdbc();
        var owner = UUID.randomUUID();
        var warehouse = warehouse(jdbc);
        var first = activeVariant(jdbc, "RELEASE-A");
        var second = activeVariant(jdbc, "RELEASE-B");
        receive(warehouse, first, 5);
        receive(warehouse, second, 7);
        var created = reservations.create(new CreateInventoryReservationRequest(
                owner,
                "create-release-0001",
                List.of(
                        new ReservationRequestLine(warehouse.value(), second.value(), 4),
                        new ReservationRequestLine(warehouse.value(), first.value(), 2))));
        var request = command(owner, created.id().value(), "release-lifecycle-0001");

        var released = reservations.release(request);
        var replay = reservations.release(request);

        assertThat(released.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(replay).usingRecursiveComparison().isEqualTo(released);
        assertBalance(jdbc, warehouse, first, 5, 0);
        assertBalance(jdbc, warehouse, second, 7, 0);
        assertThat(movementCount(jdbc, created.id().value())).isZero();
        assertThat(eventCount(jdbc, created.id().value())).isEqualTo(2);
    }

    @Test
    void rejectsExpiredWrongOwnerAndConflictingIdempotencyWithoutSideEffects() {
        var jdbc = jdbc();
        var owner = UUID.randomUUID();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "REJECTED");
        receive(warehouse, variant, 4);
        var created = reservations.create(createRequest(owner, "create-rejected-0001", warehouse, variant, 2));

        assertThatThrownBy(
                        () -> reservations.get(UUID.randomUUID(), created.id().value()))
                .isInstanceOf(ReservationNotFoundException.class);

        var request = command(owner, created.id().value(), "release-conflict-0001");
        reservations.release(request);
        assertThatThrownBy(() -> reservations.commit(command(owner, created.id().value(), "release-conflict-0001")))
                .isInstanceOf(InventoryIdempotencyConflictException.class);
        assertBalance(jdbc, warehouse, variant, 4, 0);

        var second =
                reservations.create(createRequest(UUID.randomUUID(), "create-expired-0001", warehouse, variant, 1));
        jdbc.update("""
                update inventory_reservations
                set created_at = clock_timestamp() - interval '30 minutes',
                    expires_at = clock_timestamp() - interval '1 second'
                where id = ?
                """, second.id().value());
        assertThatThrownBy(() -> reservations.commit(
                        command(second.ownerReference().value(), second.id().value(), "commit-expired-0001")))
                .isInstanceOf(InventoryInvariantViolation.class)
                .extracting(error -> ((InventoryInvariantViolation) error).invariant())
                .isEqualTo(InventoryInvariant.RESERVATION_EXPIRED);
        assertThat(eventCount(jdbc, second.id().value())).isOne();
        assertBalance(jdbc, warehouse, variant, 4, 1);
    }

    @Test
    void concurrentCommitAndReleaseProduceExactlyOneTerminalOutcome() throws Exception {
        var jdbc = jdbc();
        var owner = UUID.randomUUID();
        var warehouse = warehouse(jdbc);
        var variant = activeVariant(jdbc, "TERMINAL-RACE");
        receive(warehouse, variant, 3);
        var created = reservations.create(createRequest(owner, "create-race-0001", warehouse, variant, 2));
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var commit = executor.submit(() -> attempt(
                    () -> reservations.commit(command(owner, created.id().value(), "commit-race-0001")), start));
            var release = executor.submit(() -> attempt(
                    () -> reservations.release(command(owner, created.id().value(), "release-race-0001")), start));
            start.countDown();

            assertThat(List.of(commit.get(5, TimeUnit.SECONDS), release.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        var outcome = reservations.get(owner, created.id().value());
        assertThat(outcome.status()).isIn(ReservationStatus.COMMITTED, ReservationStatus.RELEASED);
        assertThat(eventCount(jdbc, created.id().value())).isEqualTo(2);
        if (outcome.status() == ReservationStatus.COMMITTED) {
            assertBalance(jdbc, warehouse, variant, 1, 0);
            assertThat(movementCount(jdbc, created.id().value())).isOne();
        } else {
            assertBalance(jdbc, warehouse, variant, 3, 0);
            assertThat(movementCount(jdbc, created.id().value())).isZero();
        }
    }

    private static boolean attempt(Runnable command, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            command.run();
            return true;
        } catch (InventoryInvariantViolation exception) {
            return false;
        }
    }

    private void receive(WarehouseId warehouse, CatalogVariantId variant, long quantity) {
        stock.receive(new ReceiveStockCommand(
                warehouse, variant, new StockQuantity(quantity), new MovementReason("Lifecycle fixture"), null));
    }

    private static CreateInventoryReservationRequest createRequest(
            UUID owner, String key, WarehouseId warehouse, CatalogVariantId variant, long quantity) {
        return new CreateInventoryReservationRequest(
                owner, key, List.of(new ReservationRequestLine(warehouse.value(), variant.value(), quantity)));
    }

    private static ReservationCommandRequest command(UUID owner, UUID reservation, String key) {
        return new ReservationCommandRequest(owner, reservation, key);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static WarehouseId warehouse(JdbcTemplate jdbc) {
        return new WarehouseId(requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class)));
    }

    private static void assertBalance(
            JdbcTemplate jdbc, WarehouseId warehouse, CatalogVariantId variant, long onHand, long reserved) {
        assertThat(jdbc.queryForMap(
                        "select on_hand, reserved from inventory_balances where warehouse_id = ? and variant_id = ?",
                        warehouse.value(),
                        variant.value()))
                .containsEntry("on_hand", onHand)
                .containsEntry("reserved", reserved);
    }

    private static int movementCount(JdbcTemplate jdbc, UUID reservationId) {
        return requireNonNull(jdbc.queryForObject(
                "select count(*) from inventory_movements where reservation_id = ?", Integer.class, reservationId));
    }

    private static int eventCount(JdbcTemplate jdbc, UUID reservationId) {
        return requireNonNull(jdbc.queryForObject(
                "select count(*) from inventory_reservation_events where reservation_id = ?",
                Integer.class,
                reservationId));
    }

    private static CatalogVariantId activeVariant(JdbcTemplate jdbc, String suffix) {
        var normalized = suffix.toLowerCase(Locale.ROOT);
        var category =
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_categories (id, slug, name, display_order, status)
                values (uuidv7(), ?, ?, 0, 'ACTIVE') returning id
                """, UUID.class, "lifecycle-" + normalized, "Lifecycle " + suffix));
        var product = requireNonNull(
                jdbc.queryForObject("""
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description,
                    status, primary_category_id, published_at
                ) values (uuidv7(), ?, ?, 'fixture', 'fixture', 'ACTIVE', ?, current_timestamp)
                returning id
                """, UUID.class, "lifecycle-product-" + normalized, "Product " + suffix, category));
        return new CatalogVariantId(
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature
                ) values (uuidv7(), ?, ?, 'Fixture', 'ACTIVE', 0, ?)
                returning id
                """, UUID.class, product, "LIFE-" + suffix, "fixture=" + suffix)));
    }
}
