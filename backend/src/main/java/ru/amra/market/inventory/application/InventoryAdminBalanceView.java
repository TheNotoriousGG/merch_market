package ru.amra.market.inventory.application;

import java.time.Instant;
import java.util.UUID;

/** Exact warehouse balance projection with representation metadata. */
public record InventoryAdminBalanceView(
        String warehouseCode,
        UUID warehouseId,
        UUID variantId,
        long onHand,
        long reserved,
        long version,
        Instant updatedAt) {

    /** Computes non-negative units currently available for reservation. */
    public long available() {
        return onHand - reserved;
    }
}
