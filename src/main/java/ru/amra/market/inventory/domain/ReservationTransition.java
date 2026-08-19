package ru.amra.market.inventory.domain;

/** New reservation snapshot and the immutable event produced with it. */
public record ReservationTransition(InventoryReservation reservation, ReservationEvent event) {

    /** Rejects an incomplete transition result. */
    public ReservationTransition {
        if (reservation == null || event == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_RESERVATION, "Reservation transition must contain snapshot and event");
        }
    }
}
