package ru.amra.market.inventory.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Complete bounded primary-warehouse workspace projection for administration. */
public record InventoryWarehouseOverview(List<StockItem> items, List<Movement> movements) {
    public InventoryWarehouseOverview {
        items = List.copyOf(items);
        movements = List.copyOf(movements);
    }

    public record StockItem(
            UUID productId,
            String productName,
            String productStatus,
            UUID variantId,
            String sku,
            String variantLabel,
            long onHand,
            long reserved,
            long available,
            long version,
            @Nullable Instant updatedAt) {}

    public record Movement(
            UUID id,
            UUID variantId,
            String productName,
            String sku,
            String variantLabel,
            String type,
            long quantityDelta,
            String reason,
            @Nullable String reference,
            Instant occurredAt) {}
}
