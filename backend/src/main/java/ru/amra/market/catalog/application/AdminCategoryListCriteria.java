package ru.amra.market.catalog.application;

import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.CategoryStatus;

/** Validated administrative category-list filters. */
public record AdminCategoryListCriteria(
        @Nullable String query, @Nullable CategoryStatus status) {

    public AdminCategoryListCriteria {
        query = query == null ? null : query.strip();
        if (query != null && (query.isEmpty() || query.length() > 160)) {
            throw new IllegalArgumentException("Admin category query length must be between 1 and 160");
        }
    }
}
