package ru.amra.market.catalog.domain;

import java.io.Serial;
import java.util.List;

/** Raised when a product command would violate one or more approved domain invariants. */
public final class ProductInvariantViolation extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ProductInvariant invariant;
    private final List<String> violations;

    /** Creates a violation with a stable identifier and safe validation details. */
    public ProductInvariantViolation(ProductInvariant invariant, String message) {
        this(invariant, List.of(message));
    }

    /** Creates a violation with a stable identifier and an immutable validation report. */
    public ProductInvariantViolation(ProductInvariant invariant, List<String> violations) {
        super(String.join("; ", violations));
        this.invariant = invariant;
        this.violations = List.copyOf(violations);
    }

    public ProductInvariant invariant() {
        return invariant;
    }

    public List<String> violations() {
        return violations;
    }
}
