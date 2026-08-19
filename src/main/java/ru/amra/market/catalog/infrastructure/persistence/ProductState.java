package ru.amra.market.catalog.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.MediaId;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductContent;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductStatus;
import ru.amra.market.catalog.domain.Sku;
import ru.amra.market.catalog.domain.VariantId;
import ru.amra.market.catalog.domain.VariantStatus;

record ProductState(
        ProductId id,
        ProductSlug slug,
        Set<ProductSlug> aliases,
        ProductContent content,
        ProductStatus status,
        CategoryId primaryCategoryId,
        Set<CategoryId> categoryIds,
        Set<CollectionId> collectionIds,
        List<AttributeValue> characteristics,
        List<VariantState> variants,
        List<MediaState> media,
        @Nullable Instant publishedAt,
        long version) {

    static ProductState from(Product product) {
        return new ProductState(
                product.id(),
                product.slug(),
                product.aliases(),
                product.content(),
                product.status(),
                product.primaryCategoryId(),
                product.categoryIds(),
                product.collectionIds(),
                product.characteristics(),
                product.variants().stream().map(VariantState::from).toList(),
                product.media().stream().map(MediaState::from).toList(),
                product.publishedAt().orElse(null),
                product.version());
    }

    record VariantState(
            VariantId id,
            Sku sku,
            String label,
            VariantStatus status,
            int displayOrder,
            List<AttributeValue> attributes,
            long version) {
        static VariantState from(ru.amra.market.catalog.domain.ProductVariant variant) {
            return new VariantState(
                    variant.id(),
                    variant.sku(),
                    variant.label(),
                    variant.status(),
                    variant.displayOrder(),
                    variant.attributes(),
                    variant.version());
        }
    }

    record MediaState(
            MediaId id,
            @Nullable VariantId variantId,
            String objectKey,
            String contentType,
            int width,
            int height,
            String alt,
            int displayOrder,
            boolean primary,
            long version) {
        static MediaState from(ru.amra.market.catalog.domain.ProductMedia media) {
            return new MediaState(
                    media.id(),
                    media.variantId().orElse(null),
                    media.objectKey(),
                    media.contentType(),
                    media.width(),
                    media.height(),
                    media.alt(),
                    media.displayOrder(),
                    media.primary(),
                    media.version());
        }
    }
}
