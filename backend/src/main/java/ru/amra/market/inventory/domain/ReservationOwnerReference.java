package ru.amra.market.inventory.domain;

import java.util.UUID;

/** Opaque non-PII reference owned by the calling bounded context. */
public record ReservationOwnerReference(UUID value) {

    /** Rejects a missing owner reference. */
    public ReservationOwnerReference {
        if (value == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_ID, "Reservation owner reference must not be null");
        }
    }
}
