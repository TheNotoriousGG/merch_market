package ru.amra.market.catalog.application;

/** Administrative editorial collection absence signal. */
public final class CatalogCollectionNotFoundException extends RuntimeException {

    public CatalogCollectionNotFoundException() {
        super("Catalog collection was not found");
    }
}
