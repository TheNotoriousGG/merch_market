package ru.amra.market.catalog.application;

/** Signals that a variant or media identifier is not owned by the requested product. */
public final class CatalogProductChildNotFoundException extends RuntimeException {

    public CatalogProductChildNotFoundException() {
        super("Catalog product child was not found");
    }
}
