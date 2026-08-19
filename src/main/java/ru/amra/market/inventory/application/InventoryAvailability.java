package ru.amra.market.inventory.application;

import java.util.UUID;

/** Public availability for one catalog variant. */
public record InventoryAvailability(UUID variantId, AvailabilityStatus status) {}
