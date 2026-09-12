package ru.amra.market.catalog.domain;

import java.util.UUID;

/** Strongly typed category identity. UUID generation belongs to an application port. */
public record CategoryId(UUID value) implements Comparable<CategoryId> {

    /** Creates a validated category identifier. */
    public CategoryId {
        if (value == null) {
            throw new CategoryInvariantViolation(CategoryInvariant.INVALID_ID, "Category id must not be null");
        }
    }

    @Override
    public int compareTo(CategoryId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
