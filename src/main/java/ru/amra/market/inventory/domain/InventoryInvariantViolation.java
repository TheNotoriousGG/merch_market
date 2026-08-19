package ru.amra.market.inventory.domain;

import java.io.Serial;

/** Raised when an inventory command would violate a stable domain invariant. */
public final class InventoryInvariantViolation extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final InventoryInvariant invariant;

    /** Creates a safe machine-readable domain violation. */
    public InventoryInvariantViolation(InventoryInvariant invariant, String message) {
        super(message);
        this.invariant = invariant;
    }

    /** Returns the invariant used by application error mapping. */
    public InventoryInvariant invariant() {
        return invariant;
    }
}
