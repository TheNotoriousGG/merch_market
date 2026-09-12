package ru.amra.market.catalog.application;

import java.time.Instant;
import ru.amra.market.catalog.domain.Product;

/** Administrative product aggregate plus persistence metadata and root ETag. */
public record AdminProductView(Product product, Instant createdAt, Instant updatedAt, String etag) {}
