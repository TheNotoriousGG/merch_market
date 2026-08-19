package ru.amra.market.catalog.application;

/** Administrative product absence signal. */
public final class AdminCatalogProductNotFoundException extends RuntimeException {

    public AdminCatalogProductNotFoundException() {
        super("Administrative catalog product was not found");
    }
}
