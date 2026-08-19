package ru.amra.market.inventory.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Immutable exact balance aggregate for one warehouse and catalog variant. */
public final class InventoryBalance {

    private final WarehouseId warehouseId;
    private final CatalogVariantId variantId;
    private final StockQuantity onHand;
    private final StockQuantity reserved;
    private final long version;

    private InventoryBalance(
            WarehouseId warehouseId,
            CatalogVariantId variantId,
            StockQuantity onHand,
            StockQuantity reserved,
            long version) {
        if (warehouseId == null || variantId == null || onHand == null || reserved == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_ID, "Balance identity and quantities must not be null");
        }
        if (reserved.compareTo(onHand) > 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.RESERVED_EXCEEDS_ON_HAND, "Reserved quantity must not exceed on-hand");
        }
        if (version < 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_VERSION, "Balance version must not be negative");
        }
        this.warehouseId = warehouseId;
        this.variantId = variantId;
        this.onHand = onHand;
        this.reserved = reserved;
        this.version = version;
    }

    /** Creates an empty balance before the first physical receipt. */
    public static InventoryBalance empty(WarehouseId warehouseId, CatalogVariantId variantId) {
        return new InventoryBalance(warehouseId, variantId, StockQuantity.ZERO, StockQuantity.ZERO, 0);
    }

    /** Restores a persisted balance without applying a mutation. */
    public static InventoryBalance restore(
            WarehouseId warehouseId,
            CatalogVariantId variantId,
            StockQuantity onHand,
            StockQuantity reserved,
            long version) {
        return new InventoryBalance(warehouseId, variantId, onHand, reserved, version);
    }

    /** Applies a positive physical receipt and returns matching balance/movement snapshots. */
    public InventoryMutation receive(
            MovementId movementId,
            StockQuantity quantity,
            MovementReason reason,
            @Nullable MovementReference reference,
            Instant occurredAt) {
        quantity.requirePositive();
        var next = copy(onHand.plus(quantity), reserved);
        return mutation(
                next, movementId, InventoryMovementType.RECEIPT, quantity.value(), reason, reference, occurredAt);
    }

    /** Reconciles on-hand to an exact physical count without invalidating active reservations. */
    public InventoryMutation reconcile(
            MovementId movementId,
            StockQuantity targetOnHand,
            MovementReason reason,
            @Nullable MovementReference reference,
            Instant occurredAt) {
        if (targetOnHand.compareTo(reserved) < 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.RESERVED_EXCEEDS_ON_HAND,
                    "Physical on-hand cannot be reconciled below active reservations");
        }
        var delta = targetOnHand.value() - onHand.value();
        if (delta == 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.NO_PHYSICAL_CHANGE, "Physical reconciliation must change on-hand");
        }
        var type = delta > 0 ? InventoryMovementType.ADJUSTMENT_INCREASE : InventoryMovementType.ADJUSTMENT_DECREASE;
        var next = copy(targetOnHand, reserved);
        return mutation(next, movementId, type, delta, reason, reference, occurredAt);
    }

    /** Reserves available units without changing physical on-hand. */
    public InventoryBalance reserve(StockQuantity quantity) {
        quantity.requirePositive();
        if (quantity.compareTo(available()) > 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INSUFFICIENT_STOCK, "Available stock is insufficient for reservation");
        }
        return copy(onHand, reserved.plus(quantity));
    }

    /** Releases units held by an active reservation without a physical movement. */
    public InventoryBalance release(StockQuantity quantity) {
        quantity.requirePositive();
        return copy(onHand, reserved.minus(quantity, InventoryInvariant.RESERVED_UNDERFLOW));
    }

    /** Commits reserved units as a physical outgoing movement. */
    public InventoryMutation commit(
            MovementId movementId,
            StockQuantity quantity,
            MovementReason reason,
            @Nullable MovementReference reference,
            Instant occurredAt) {
        quantity.requirePositive();
        var nextReserved = reserved.minus(quantity, InventoryInvariant.RESERVED_UNDERFLOW);
        var nextOnHand = onHand.minus(quantity, InventoryInvariant.INSUFFICIENT_STOCK);
        var next = copy(nextOnHand, nextReserved);
        return mutation(
                next,
                movementId,
                InventoryMovementType.RESERVATION_COMMIT,
                -quantity.value(),
                reason,
                reference,
                occurredAt);
    }

    public WarehouseId warehouseId() {
        return warehouseId;
    }

    public CatalogVariantId variantId() {
        return variantId;
    }

    public StockQuantity onHand() {
        return onHand;
    }

    public StockQuantity reserved() {
        return reserved;
    }

    public StockQuantity available() {
        return new StockQuantity(onHand.value() - reserved.value());
    }

    public long version() {
        return version;
    }

    private InventoryBalance copy(StockQuantity nextOnHand, StockQuantity nextReserved) {
        try {
            return new InventoryBalance(warehouseId, variantId, nextOnHand, nextReserved, Math.incrementExact(version));
        } catch (ArithmeticException exception) {
            throw new InventoryInvariantViolation(InventoryInvariant.VERSION_OVERFLOW, "Balance version overflowed");
        }
    }

    private InventoryMutation mutation(
            InventoryBalance balance,
            MovementId movementId,
            InventoryMovementType type,
            long delta,
            MovementReason reason,
            @Nullable MovementReference reference,
            Instant occurredAt) {
        return new InventoryMutation(
                balance,
                new InventoryMovement(movementId, warehouseId, variantId, type, delta, reason, reference, occurredAt));
    }
}
