package ru.amra.market.catalog.domain;

import java.util.UUID;

/** Strongly typed product identity. */
public record ProductId(UUID value) implements Comparable<ProductId> {
    public ProductId {
        if (value == null) {
            throw new ProductInvariantViolation(ProductInvariant.INVALID_ID, "Product id must not be null");
        }
    }

    @Override
    public int compareTo(ProductId other) {
        return value.compareTo(other.value);
    }
}
