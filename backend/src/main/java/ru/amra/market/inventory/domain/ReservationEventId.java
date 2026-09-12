package ru.amra.market.inventory.domain;

import java.util.UUID;

/** Strongly typed reservation event identity. */
public record ReservationEventId(UUID value) {

    /** Rejects a missing identity. */
    public ReservationEventId {
        if (value == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_ID, "Reservation event id must not be null");
        }
    }
}
