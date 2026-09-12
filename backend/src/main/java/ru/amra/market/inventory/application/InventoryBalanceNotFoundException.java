package ru.amra.market.inventory.application;

import java.io.Serial;

/** Signals that an exact balance command targeted an absent balance. */
public final class InventoryBalanceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InventoryBalanceNotFoundException() {
        super("Inventory balance was not found");
    }
}
