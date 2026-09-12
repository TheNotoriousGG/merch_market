package ru.amra.market.inventory.application;

import java.io.Serial;

/** Signals an optimistic balance version mismatch. */
public final class StaleInventoryVersionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StaleInventoryVersionException() {
        super("Inventory balance version is stale");
    }
}
