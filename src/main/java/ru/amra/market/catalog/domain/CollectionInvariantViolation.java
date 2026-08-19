package ru.amra.market.catalog.domain;

/** Raised when an editorial collection command violates a stable domain rule. */
public final class CollectionInvariantViolation extends RuntimeException {

    private final CollectionInvariant invariant;

    public CollectionInvariantViolation(CollectionInvariant invariant, String message) {
        super(message);
        this.invariant = invariant;
    }

    public CollectionInvariant invariant() {
        return invariant;
    }
}
