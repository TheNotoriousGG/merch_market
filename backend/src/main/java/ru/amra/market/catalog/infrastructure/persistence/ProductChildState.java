package ru.amra.market.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Set;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.ProductMedia;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductVariant;

record ProductChildState(
        Set<ProductSlug> aliases,
        Set<CategoryId> categoryIds,
        Set<CollectionId> collectionIds,
        List<AttributeValue> characteristics,
        List<ProductVariant> variants,
        List<ProductMedia> media) {}
