package ru.amra.market.catalog.application;

/** Public-safe absence signal that deliberately hides non-public product lifecycle state. */
public final class CatalogProductNotFoundException extends RuntimeException {

    public CatalogProductNotFoundException() {
        super("Published catalog product was not found");
    }
}
