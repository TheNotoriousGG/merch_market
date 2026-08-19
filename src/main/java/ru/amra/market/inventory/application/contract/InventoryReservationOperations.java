package ru.amra.market.inventory.application.contract;

import ru.amra.market.inventory.domain.InventoryReservation;

/** Inventory reservation contract for future ordering composition. */
public interface InventoryReservationOperations {
    /** Creates or replays an all-or-nothing reservation. */
    InventoryReservation create(CreateInventoryReservationRequest request);
}
