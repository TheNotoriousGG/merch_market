package ru.amra.market.inventory.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Immutable evidence of one physical on-hand change. */
public record InventoryMovement(
        MovementId id,
        WarehouseId warehouseId,
        CatalogVariantId variantId,
        InventoryMovementType type,
        long quantityDelta,
        MovementReason reason,
        @Nullable MovementReference reference,
        Instant occurredAt) {

    /** Validates identity, sign and occurrence metadata for an immutable movement. */
    public InventoryMovement {
        if (id == null || warehouseId == null || variantId == null || type == null || reason == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_MOVEMENT, "Movement identity and type must not be null");
        }
        if (!type.accepts(quantityDelta)) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_MOVEMENT, "Movement delta direction does not match its type");
        }
        if (occurredAt == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_MOVEMENT, "Movement occurrence time must not be null");
        }
    }
}
