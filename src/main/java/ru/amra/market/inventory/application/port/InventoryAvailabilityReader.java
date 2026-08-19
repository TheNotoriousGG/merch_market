package ru.amra.market.inventory.application.port;

import java.util.Set;
import java.util.UUID;

/** Bounded projection for variants with positive exact availability. */
public interface InventoryAvailabilityReader {
    /** Returns supplied identifiers whose aggregate available quantity is positive. */
    Set<UUID> findInStock(Set<UUID> variantIds);
}
