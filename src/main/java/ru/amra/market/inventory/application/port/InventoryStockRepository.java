package ru.amra.market.inventory.application.port;

import java.util.Optional;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryBalance;
import ru.amra.market.inventory.domain.InventoryMutation;
import ru.amra.market.inventory.domain.WarehouseId;

/** Transaction-bound persistence boundary for exact balances and physical movements. */
public interface InventoryStockRepository {

    /** Creates an empty balance when absent and locks the row for the current transaction. */
    InventoryBalance lockOrCreate(WarehouseId warehouseId, CatalogVariantId variantId);

    /** Locks an existing balance row for the current transaction. */
    Optional<InventoryBalance> lock(WarehouseId warehouseId, CatalogVariantId variantId);

    /** Reads an exact balance without a write lock. */
    Optional<InventoryBalance> find(WarehouseId warehouseId, CatalogVariantId variantId);

    /** Optimistically updates a balance and appends its matching immutable movement. */
    void save(InventoryMutation mutation);

    /** Returns the signed sum of all physical movements for reconciliation. */
    long physicalTotal(WarehouseId warehouseId, CatalogVariantId variantId);
}
