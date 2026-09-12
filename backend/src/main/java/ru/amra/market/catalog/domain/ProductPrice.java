package ru.amra.market.catalog.domain;

/** Product selling price in minor currency units. The catalog currently operates in RUB only. */
public record ProductPrice(long minorUnits) {
    public ProductPrice {
        if (minorUnits <= 0) {
            throw new ProductInvariantViolation(
                    ProductInvariant.PRODUCT_NOT_PUBLISHABLE, "Product price must be positive");
        }
    }
}
