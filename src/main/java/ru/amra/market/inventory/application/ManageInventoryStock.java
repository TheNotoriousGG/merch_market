package ru.amra.market.inventory.application;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.inventory.application.port.InventoryIdGenerator;
import ru.amra.market.inventory.application.port.InventoryStockRepository;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryBalance;
import ru.amra.market.inventory.domain.InventoryMutation;
import ru.amra.market.inventory.domain.MovementId;
import ru.amra.market.inventory.domain.WarehouseId;

/** Transactional physical inventory command facade. */
@Service
public class ManageInventoryStock {

    private final InventoryStockRepository stock;
    private final InventoryIdGenerator ids;
    private final Clock clock;

    public ManageInventoryStock(InventoryStockRepository stock, InventoryIdGenerator ids, Clock clock) {
        this.stock = stock;
        this.ids = ids;
        this.clock = clock;
    }

    /** Atomically creates/locks a balance, applies a receipt and appends its movement. */
    @Transactional
    public InventoryMutation receive(ReceiveStockCommand command) {
        var current = stock.lockOrCreate(command.warehouseId(), command.variantId());
        var mutation = current.receive(
                new MovementId(ids.next()), command.quantity(), command.reason(), command.reference(), clock.instant());
        stock.save(mutation);
        assertReconciled(mutation.balance());
        return mutation;
    }

    /** Atomically applies an exact count when its strong version precondition is current. */
    @Transactional
    public InventoryMutation reconcile(ReconcileStockCommand command) {
        var current = stock.lock(command.warehouseId(), command.variantId())
                .orElseThrow(InventoryBalanceNotFoundException::new);
        if (current.version() != command.expectedVersion()) {
            throw new StaleInventoryVersionException();
        }
        var mutation = current.reconcile(
                new MovementId(ids.next()),
                command.targetOnHand(),
                command.reason(),
                command.reference(),
                clock.instant());
        stock.save(mutation);
        assertReconciled(mutation.balance());
        return mutation;
    }

    /** Reads an exact balance without taking a write lock. */
    @Transactional(readOnly = true)
    public InventoryBalance get(WarehouseId warehouseId, CatalogVariantId variantId) {
        return stock.find(warehouseId, variantId).orElseThrow(InventoryBalanceNotFoundException::new);
    }

    /** Verifies one balance against the immutable physical ledger. */
    @Transactional(readOnly = true)
    public void verifyPhysicalLedger(WarehouseId warehouseId, CatalogVariantId variantId) {
        assertReconciled(get(warehouseId, variantId));
    }

    private void assertReconciled(InventoryBalance balance) {
        if (stock.physicalTotal(balance.warehouseId(), balance.variantId())
                != balance.onHand().value()) {
            throw new InventoryLedgerMismatchException();
        }
    }
}
