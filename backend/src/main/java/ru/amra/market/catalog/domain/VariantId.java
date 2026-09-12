package ru.amra.market.catalog.domain;

import java.util.UUID;

/** Strongly typed product variant identity. */
public record VariantId(UUID value) {
    public VariantId {
        if (value == null) {
            throw new ProductInvariantViolation(ProductInvariant.INVALID_ID, "Variant id must not be null");
        }
    }
}
