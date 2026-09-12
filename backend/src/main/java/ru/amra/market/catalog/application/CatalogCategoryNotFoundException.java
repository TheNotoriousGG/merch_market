package ru.amra.market.catalog.application;

/** Administrative category identity does not exist. */
public final class CatalogCategoryNotFoundException extends RuntimeException {

    public CatalogCategoryNotFoundException() {
        super("Catalog category was not found");
    }
}
