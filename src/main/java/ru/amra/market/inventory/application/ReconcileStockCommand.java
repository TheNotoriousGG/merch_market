package ru.amra.market.inventory.application;

import org.jspecify.annotations.Nullable;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.MovementReference;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** Exact physical-count command guarded by the current balance version. */
public record ReconcileStockCommand(
        WarehouseId warehouseId,
        CatalogVariantId variantId,
        StockQuantity targetOnHand,
        long expectedVersion,
        MovementReason reason,
        @Nullable MovementReference reference) {}
