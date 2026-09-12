package ru.amra.market.inventory.application;

/** Signals that at least one requested catalog variant is not active. */
public final class InventoryVariantNotActiveException extends RuntimeException {
    public InventoryVariantNotActiveException() {
        super("At least one inventory variant is not active");
    }
}
