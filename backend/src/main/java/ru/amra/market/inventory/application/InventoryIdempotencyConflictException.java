package ru.amra.market.inventory.application;

/** Signals reuse of an inventory idempotency key for another command. */
public final class InventoryIdempotencyConflictException extends RuntimeException {
    public InventoryIdempotencyConflictException() {
        super("Idempotency key is bound to a different inventory command");
    }
}
