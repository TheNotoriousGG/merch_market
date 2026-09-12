package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.amra.market.inventory.application.InventoryLedgerMismatchException;
import ru.amra.market.inventory.application.ManageInventoryStock;
import ru.amra.market.inventory.application.ReceiveStockCommand;
import ru.amra.market.inventory.application.ReconcileStockCommand;
import ru.amra.market.inventory.application.StaleInventoryVersionException;
import ru.amra.market.inventory.application.port.InventoryStockRepository;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.MovementId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.MovementReference;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class InventoryStockPersistenceIntegrationTest extends PostgreSqlIntegrationTest {

    private static final MovementReason RECEIPT_REASON = new MovementReason("Supplier receipt");

    @Autowired
    private ManageInventoryStock stock;

    @Autowired
    private InventoryStockRepository repository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void receiptCreatesBalanceAndMatchingPhysicalMovementAtomically() {
        var jdbc = jdbc();
        var warehouseId = primaryWarehouseId(jdbc);
        var variantId = new CatalogVariantId(uuidV7(jdbc));

        var result = stock.receive(new ReceiveStockCommand(
                warehouseId,
                variantId,
                new StockQuantity(12),
                RECEIPT_REASON,
                new MovementReference("SUPPLY-2026-001")));

        assertThat(result.balance().onHand().value()).isEqualTo(12);
        assertThat(result.balance().reserved().value()).isZero();
        assertThat(result.balance().version()).isOne();
        assertThat(result.movement().quantityDelta()).isEqualTo(12);
        assertThat(jdbc.queryForObject("""
                        select count(*) from inventory_movements
                        where warehouse_id = ? and variant_id = ?
                        """, Integer.class, warehouseId.value(), variantId.value()))
                .isOne();
        stock.verifyPhysicalLedger(warehouseId, variantId);
    }

    @Test
    void reconciliationUsesExpectedVersionAndAppendsSignedAdjustment() {
        var jdbc = jdbc();
        var warehouseId = primaryWarehouseId(jdbc);
        var variantId = new CatalogVariantId(uuidV7(jdbc));
        var received = stock.receive(
                new ReceiveStockCommand(warehouseId, variantId, new StockQuantity(10), RECEIPT_REASON, null));

        var adjusted = stock.reconcile(new ReconcileStockCommand(
                warehouseId,
                variantId,
                new StockQuantity(7),
                received.balance().version(),
                new MovementReason("Cycle count"),
                null));

        assertThat(adjusted.balance().onHand().value()).isEqualTo(7);
        assertThat(adjusted.balance().version()).isEqualTo(2);
        assertThat(adjusted.movement().quantityDelta()).isEqualTo(-3);
        assertThat(jdbc.queryForObject("""
                        select sum(quantity_delta)::bigint from inventory_movements
                        where warehouse_id = ? and variant_id = ?
                        """, Long.class, warehouseId.value(), variantId.value()))
                .isEqualTo(7);
    }

    @Test
    void staleReconciliationDoesNotAppendMovement() {
        var jdbc = jdbc();
        var warehouseId = primaryWarehouseId(jdbc);
        var variantId = new CatalogVariantId(uuidV7(jdbc));
        stock.receive(new ReceiveStockCommand(warehouseId, variantId, new StockQuantity(5), RECEIPT_REASON, null));

        assertThatThrownBy(() -> stock.reconcile(new ReconcileStockCommand(
                        warehouseId, variantId, new StockQuantity(4), 0, new MovementReason("Stale count"), null)))
                .isInstanceOf(StaleInventoryVersionException.class);

        assertThat(jdbc.queryForObject("""
                        select count(*) from inventory_movements
                        where warehouse_id = ? and variant_id = ?
                        """, Integer.class, warehouseId.value(), variantId.value()))
                .isOne();
        assertThat(stock.get(warehouseId, variantId).onHand().value()).isEqualTo(5);
    }

    @Test
    void failedMovementInsertRollsBackBalanceUpdate() {
        var jdbc = jdbc();
        var warehouseId = primaryWarehouseId(jdbc);
        var variantId = new CatalogVariantId(uuidV7(jdbc));
        var initial = stock.receive(
                new ReceiveStockCommand(warehouseId, variantId, new StockQuantity(5), RECEIPT_REASON, null));
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
                    var current = repository.lock(warehouseId, variantId).orElseThrow();
                    var mutation = current.receive(
                            new MovementId(new UUID(0, 99)), new StockQuantity(2), RECEIPT_REASON, null, Instant.now());
                    repository.save(mutation);
                }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("ck_inventory_movements__uuid_v7");

        var afterFailure = stock.get(warehouseId, variantId);
        assertThat(afterFailure.onHand()).isEqualTo(initial.balance().onHand());
        assertThat(afterFailure.version()).isEqualTo(initial.balance().version());
        assertThat(jdbc.queryForObject("""
                        select count(*) from inventory_movements
                        where warehouse_id = ? and variant_id = ?
                        """, Integer.class, warehouseId.value(), variantId.value()))
                .isOne();
    }

    @Test
    void reconciliationFailsClosedWhenSnapshotHasNoMatchingLedger() {
        var jdbc = jdbc();
        var warehouseId = primaryWarehouseId(jdbc);
        var variantId = new CatalogVariantId(uuidV7(jdbc));
        jdbc.update("""
                insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved)
                values (?, ?, 5, 0)
                """, warehouseId.value(), variantId.value());

        assertThatThrownBy(() -> stock.verifyPhysicalLedger(warehouseId, variantId))
                .isInstanceOf(InventoryLedgerMismatchException.class);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    private static WarehouseId primaryWarehouseId(JdbcTemplate jdbc) {
        return new WarehouseId(requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class)));
    }

    private static UUID uuidV7(JdbcTemplate jdbc) {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
