package ru.amra.market.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public detail representation resolved from the current product aggregate. */
public record CatalogProductDetail(
        UUID id,
        String slug,
        String name,
        String shortDescription,
        String description,
        List<CategorySummary> categories,
        List<CollectionSummary> collections,
        List<CatalogProductPage.AttributeValue> characteristics,
        List<CatalogProductPage.Media> media,
        List<Variant> variants,
        Instant publishedAt,
        String etag) {

    public CatalogProductDetail {
        categories = List.copyOf(categories);
        collections = List.copyOf(collections);
        characteristics = List.copyOf(characteristics);
        media = List.copyOf(media);
        variants = List.copyOf(variants);
    }

    public record CategorySummary(UUID id, String slug, String name) {}

    public record CollectionSummary(UUID id, String slug, String name) {}

    public record Variant(
            UUID id, String sku, String label, int displayOrder, List<CatalogProductPage.AttributeValue> attributes) {

        public Variant {
            attributes = List.copyOf(attributes);
        }
    }
}
