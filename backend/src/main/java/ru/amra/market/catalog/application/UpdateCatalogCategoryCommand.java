package ru.amra.market.catalog.application;

import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryStatus;

/** Partial administrative category change guarded by an expected aggregate version. */
public record UpdateCatalogCategoryCommand(
        CategoryId id,
        long expectedVersion,
        boolean parentSpecified,
        @Nullable CategoryId parentId,
        @Nullable String slug,
        @Nullable String name,
        @Nullable Integer displayOrder,
        @Nullable CategoryStatus status) {

    public UpdateCatalogCategoryCommand {
        if (!parentSpecified && parentId != null) {
            throw new IllegalArgumentException("Unspecified category parent cannot carry a value");
        }
        if (!parentSpecified && slug == null && name == null && displayOrder == null && status == null) {
            throw new IllegalArgumentException("Category update must contain at least one change");
        }
    }
}
