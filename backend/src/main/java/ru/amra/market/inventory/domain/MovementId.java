package ru.amra.market.inventory.domain;

import java.util.UUID;

/** Strong identity of an immutable physical inventory movement. */
public record MovementId(UUID value) {

    /** Creates a validated movement identifier. */
    public MovementId {
        if (value == null) {
            throw new InventoryInvariantViolation(InventoryInvariant.INVALID_ID, "Movement id must not be null");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
