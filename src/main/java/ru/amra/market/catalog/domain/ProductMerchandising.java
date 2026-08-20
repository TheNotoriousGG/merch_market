package ru.amra.market.catalog.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Catalog-manager controlled storefront merchandising flags. */
public record ProductMerchandising(
        boolean newArrival,
        @Nullable Instant newUntil,
        boolean onSale,
        @Nullable Integer salePercent) {

    public ProductMerchandising {
        if (!newArrival && newUntil != null) {
            throw new IllegalArgumentException("New-until date requires the new-arrival flag");
        }
        if (onSale && (salePercent == null || salePercent < 1 || salePercent > 90)) {
            throw new IllegalArgumentException("Sale percent must be between 1 and 90");
        }
        if (!onSale && salePercent != null) {
            throw new IllegalArgumentException("Sale percent requires the sale flag");
        }
    }

    public static ProductMerchandising none() {
        return new ProductMerchandising(false, null, false, null);
    }

    public boolean isNewAt(Instant instant) {
        return newArrival && (newUntil == null || newUntil.isAfter(instant));
    }
}
