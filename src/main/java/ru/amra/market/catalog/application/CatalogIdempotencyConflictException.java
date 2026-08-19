package ru.amra.market.catalog.application;

/** The caller reused an idempotency key for a semantically different command. */
public final class CatalogIdempotencyConflictException extends RuntimeException {

    public CatalogIdempotencyConflictException() {
        super("Idempotency key is already bound to a different catalog command");
    }
}
