package ru.amra.market.catalog.application;

import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.CategoryId;

/** Caller-scoped idempotent command for creating a hidden category. */
public record CreateCatalogCategoryCommand(
        @Nullable CategoryId parentId,
        String slug,
        String name,
        int displayOrder,
        String actorScope,
        String idempotencyKey) {}
