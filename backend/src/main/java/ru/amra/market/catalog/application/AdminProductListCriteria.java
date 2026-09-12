package ru.amra.market.catalog.application;

import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.ProductStatus;

/** Validated administrative product list filters and stable page request. */
public record AdminProductListCriteria(
        @Nullable String query,
        @Nullable ProductStatus status,
        @Nullable CategoryId categoryId,
        int page,
        int size,
        Sort sort) {

    public AdminProductListCriteria {
        query = query == null ? null : query.strip();
        if (query != null && (query.isEmpty() || query.length() > 200)) {
            throw new IllegalArgumentException("Admin product query length must be between 1 and 200");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Admin product page must be non-negative and size must be 1..100");
        }
    }

    public long offset() {
        return Math.multiplyExact((long) page, size);
    }

    public enum Sort {
        UPDATED_DESC,
        UPDATED_ASC,
        NAME_ASC,
        NAME_DESC
    }
}
