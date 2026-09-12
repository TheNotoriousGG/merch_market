package ru.amra.market.catalog.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.CategoryStatus;

/** Administrative category response with strong-version ETag. */
public record AdminCategoryView(
        UUID id,
        @Nullable UUID parentId,
        String slug,
        String name,
        int displayOrder,
        CategoryStatus status,
        long version,
        Instant updatedAt,
        String etag) {}
