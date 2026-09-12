package ru.amra.market.inventory.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryBalance;
import ru.amra.market.inventory.domain.InventoryMutation;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.WarehouseId;

/** Transaction-bound persistence boundary for exact balances and physical movements. */
public interface InventoryStockRepository {

    /** Creates an empty balance when absent and locks the row for the current transaction. */
    InventoryBalance lockOrCreate(WarehouseId warehouseId, CatalogVariantId variantId);

    /** Creates missing balances and locks all rows in deterministic identity order. */
    List<InventoryBalance> lockOrCreateAll(List<InventoryBalanceKey> keys);

    /** Locks existing balances in deterministic identity order. */
    List<InventoryBalance> lockAll(List<InventoryBalanceKey> keys);

    /** Locks an existing balance row for the current transaction. */
    Optional<InventoryBalance> lock(WarehouseId warehouseId, CatalogVariantId variantId);

    /** Reads an exact balance without a write lock. */
    Optional<InventoryBalance> find(WarehouseId warehouseId, CatalogVariantId variantId);

    /** Optimistically updates a balance and appends its matching immutable movement. */
    void save(InventoryMutation mutation);

    /** Persists a reservation-bound outgoing mutation and immutable movement. */
    void saveReservationCommit(InventoryMutation mutation, ReservationId reservationId);

    /** Optimistically persists a reserved-only mutation without a physical movement. */
    void saveBalance(InventoryBalance balance, Instant updatedAt);

    /** Returns the signed sum of all physical movements for reconciliation. */
    long physicalTotal(WarehouseId warehouseId, CatalogVariantId variantId);

    /** Stable composite identity used for lock ordering. */
    record InventoryBalanceKey(WarehouseId warehouseId, CatalogVariantId variantId)
            implements Comparable<InventoryBalanceKey> {

        @Override
        public int compareTo(InventoryBalanceKey other) {
            var warehouseOrder = warehouseId.value().compareTo(other.warehouseId.value());
            return warehouseOrder != 0 ? warehouseOrder : variantId.value().compareTo(other.variantId.value());
        }
    }
}
