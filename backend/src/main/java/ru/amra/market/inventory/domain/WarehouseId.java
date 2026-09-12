package ru.amra.market.inventory.domain;

import java.util.UUID;

/** Strong inventory-owned warehouse identity. */
public record WarehouseId(UUID value) implements Comparable<WarehouseId> {

    /** Creates a validated warehouse identifier. */
    public WarehouseId {
        if (value == null) {
            throw new InventoryInvariantViolation(InventoryInvariant.INVALID_ID, "Warehouse id must not be null");
        }
    }

    @Override
    public int compareTo(WarehouseId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
