package ru.amra.market.inventory.application;

import java.io.Serial;

/** Fails closed when an exact balance and its physical movement ledger diverge. */
public final class InventoryLedgerMismatchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InventoryLedgerMismatchException() {
        super("Inventory balance does not reconcile with its physical movement ledger");
    }
}
