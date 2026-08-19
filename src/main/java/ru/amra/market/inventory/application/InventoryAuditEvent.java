package ru.amra.market.inventory.application;

import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.MovementReference;
import ru.amra.market.inventory.domain.WarehouseId;

/** Append-only non-sensitive warehouse mutation evidence. */
public record InventoryAuditEvent(
        InventoryAuditContext context,
        Instant occurredAt,
        WarehouseId warehouseId,
        CatalogVariantId variantId,
        String action,
        MovementReason reason,
        @Nullable MovementReference reference,
        Map<String, String> safeDiff) {

    public InventoryAuditEvent {
        if (context == null || occurredAt == null || warehouseId == null || variantId == null) {
            throw new IllegalArgumentException("Inventory audit identity and time must not be null");
        }
        safeDiff = Map.copyOf(safeDiff);
    }
}
