package ru.amra.market.catalog.infrastructure.persistence;

import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductContent;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductMerchandising;
import ru.amra.market.catalog.domain.ProductPrice;
import ru.amra.market.catalog.domain.ProductSlug;

final class ProductPersistenceMapper {

    private ProductPersistenceMapper() {}

    static Product toDomain(ProductJpaEntity entity, ProductChildState children) {
        return Product.restore(
                new ProductId(entity.id()),
                new ProductSlug(entity.canonicalSlug()),
                children.aliases(),
                new ProductContent(entity.name(), entity.shortDescription(), entity.description()),
                entity.priceMinor() == null ? null : new ProductPrice(entity.priceMinor()),
                entity.status(),
                new CategoryId(entity.primaryCategoryId()),
                children.categoryIds(),
                children.collectionIds(),
                children.characteristics(),
                children.variants(),
                children.media(),
                new ProductMerchandising(entity.newArrival(), entity.newUntil(), entity.onSale(), entity.salePercent(), entity.featured()),
                entity.publishedAt(),
                entity.version());
    }
}
