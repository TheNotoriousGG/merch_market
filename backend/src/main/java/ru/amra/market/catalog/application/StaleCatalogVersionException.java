package ru.amra.market.catalog.application;

/** The supplied strong entity tag does not match the current aggregate version. */
public final class StaleCatalogVersionException extends RuntimeException {

    public StaleCatalogVersionException() {
        super("Catalog resource version is stale");
    }
}
