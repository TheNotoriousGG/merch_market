package ru.amra.market.inventory.application.contract;

import java.util.UUID;

/** One raw line in an internal reservation command. */
public record ReservationRequestLine(UUID warehouseId, UUID variantId, long quantity) {}
