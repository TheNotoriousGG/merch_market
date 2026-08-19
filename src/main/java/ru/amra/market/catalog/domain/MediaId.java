package ru.amra.market.catalog.domain;

import java.util.UUID;

/** Strongly typed product media identity. */
public record MediaId(UUID value) {
    public MediaId {
        if (value == null) {
            throw new ProductInvariantViolation(ProductInvariant.INVALID_ID, "Media id must not be null");
        }
    }
}
