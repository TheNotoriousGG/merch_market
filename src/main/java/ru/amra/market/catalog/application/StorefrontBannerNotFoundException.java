package ru.amra.market.catalog.application;

/** Administrative storefront banner absence signal. */
public final class StorefrontBannerNotFoundException extends RuntimeException {
    public StorefrontBannerNotFoundException() {
        super("Storefront banner was not found");
    }
}
