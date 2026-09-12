package ru.amra.market.inventory.domain;

/** Atomic domain result persisted as one updated balance and one immutable movement. */
public record InventoryMutation(InventoryBalance balance, InventoryMovement movement) {

    /** Creates a complete physical mutation result. */
    public InventoryMutation {
        if (balance == null || movement == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_MOVEMENT, "Mutation balance and movement must not be null");
        }
        if (!balance.warehouseId().equals(movement.warehouseId())
                || !balance.variantId().equals(movement.variantId())) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_MOVEMENT, "Mutation movement must belong to its balance");
        }
    }
}
