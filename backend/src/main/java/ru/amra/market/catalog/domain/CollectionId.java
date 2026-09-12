package ru.amra.market.catalog.domain;

import java.util.UUID;

/** Strongly typed editorial collection identity. */
public record CollectionId(UUID value) {
    public CollectionId {
        if (value == null) {
            throw new ProductInvariantViolation(ProductInvariant.INVALID_ID, "Collection id must not be null");
        }
    }
}
