package ru.amra.market.catalog.domain;

import java.io.Serial;

/** Raised when a category command would violate an approved domain invariant. */
public final class CategoryInvariantViolation extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final CategoryInvariant invariant;

    /**
     * Creates a violation carrying a stable machine-readable invariant identifier.
     *
     * @param invariant violated invariant
     * @param message diagnostic detail without sensitive data
     */
    public CategoryInvariantViolation(CategoryInvariant invariant, String message) {
        super(message);
        this.invariant = invariant;
    }

    /**
     * Returns the stable invariant identifier used by application error mapping.
     *
     * @return violated invariant
     */
    public CategoryInvariant invariant() {
        return invariant;
    }
}
