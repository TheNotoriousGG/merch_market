package ru.amra.market.catalog.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ProductFixtures {

    private ProductFixtures() {}

    static Product draft(long productId, String slug) {
        var category = categoryId(100);
        return Product.create(
                productId(productId),
                new ProductSlug(slug),
                new ProductContent("Футболка", "Короткое описание", "Полное описание товара"),
                category,
                Set.of(category),
                Set.of(),
                List.of(material()));
    }

    static Product completeDraft(long productId, String slug, String sku) {
        var draft = draft(productId, slug);
        return draft.revise(
                        draft.slug(),
                        draft.content(),
                        draft.primaryCategoryId(),
                        draft.categoryIds(),
                        draft.collectionIds(),
                        draft.characteristics(),
                        draft.merchandising(),
                        new ProductPrice(549_000))
                .addVariant(variant(productId, sku, "BLACK", "M"))
                .addMedia(primaryMedia(productId, null));
    }

    static ProductVariant variant(long id, String sku, String color, String size) {
        return ProductVariant.create(
                variantId(id),
                new Sku(sku),
                color + " / " + size,
                0,
                List.of(
                        new AttributeValue("size", "Размер", AttributeType.SIZE, size, true, 20),
                        new AttributeValue("color", "Цвет", AttributeType.COLOR, color, true, 10)));
    }

    static ProductMedia primaryMedia(long id, @org.jspecify.annotations.Nullable VariantId variantId) {
        return ProductMedia.image(
                mediaId(id),
                variantId,
                "catalog/product-" + id + "/primary.webp",
                "image/webp",
                1200,
                1500,
                "Футболка Амра",
                0,
                true);
    }

    static AttributeValue material() {
        return new AttributeValue("material", "Материал", AttributeType.TEXT, "Хлопок", false, 0);
    }

    static ProductId productId(long value) {
        return new ProductId(new UUID(0, value));
    }

    static VariantId variantId(long value) {
        return new VariantId(new UUID(0, value));
    }

    static MediaId mediaId(long value) {
        return new MediaId(new UUID(0, value));
    }

    static CategoryId categoryId(long value) {
        return new CategoryId(new UUID(0, value));
    }
}
