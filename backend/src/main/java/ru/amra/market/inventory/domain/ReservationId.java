package ru.amra.market.inventory.domain;

import java.util.UUID;

/** Strongly typed inventory reservation identity. */
public record ReservationId(UUID value) {

    /** Rejects a missing identity. */
    public ReservationId {
        if (value == null) {
            throw new InventoryInvariantViolation(InventoryInvariant.INVALID_ID, "Reservation id must not be null");
        }
    }
}
