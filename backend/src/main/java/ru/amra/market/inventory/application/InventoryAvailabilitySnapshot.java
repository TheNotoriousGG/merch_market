package ru.amra.market.inventory.application;

import java.time.Instant;
import java.util.List;

/** Point-in-time public batch availability result. */
public record InventoryAvailabilitySnapshot(List<InventoryAvailability> items, Instant asOf) {}
