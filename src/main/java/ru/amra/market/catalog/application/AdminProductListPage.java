package ru.amra.market.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.amra.market.catalog.domain.ProductStatus;

/** One immutable administrative product page independent from HTTP transport. */
public record AdminProductListPage(List<Item> items, int page, int size, long totalElements) {

    public AdminProductListPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return totalElements == 0 ? 0 : Math.toIntExact((totalElements + size - 1) / size);
    }

    public record Item(
            UUID id,
            String slug,
            String name,
            ProductStatus status,
            UUID primaryCategoryId,
            int variantCount,
            int mediaCount,
            boolean hasPrimaryMedia,
            long version,
            Instant updatedAt) {}
}
