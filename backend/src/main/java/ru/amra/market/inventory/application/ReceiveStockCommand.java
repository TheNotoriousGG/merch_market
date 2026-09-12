package ru.amra.market.inventory.application;

import org.jspecify.annotations.Nullable;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.MovementReference;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** Validated application command for a physical stock receipt. */
public record ReceiveStockCommand(
        WarehouseId warehouseId,
        CatalogVariantId variantId,
        StockQuantity quantity,
        MovementReason reason,
        @Nullable MovementReference reference) {}
