package ru.amra.market.inventory.domain;

import java.util.UUID;

/** Inventory-local typed reference to an immutable catalog variant identity. */
public record CatalogVariantId(UUID value) implements Comparable<CatalogVariantId> {

    /** Creates a validated catalog variant reference. */
    public CatalogVariantId {
        if (value == null) {
            throw new InventoryInvariantViolation(InventoryInvariant.INVALID_ID, "Variant id must not be null");
        }
    }

    @Override
    public int compareTo(CatalogVariantId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
