package ru.amra.market.catalog.application;

import java.io.Serial;

/** Signals that an aggregate command was based on a stale optimistic version. */
public final class ConcurrentCatalogModificationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConcurrentCatalogModificationException(String message) {
        super(message);
    }
}
