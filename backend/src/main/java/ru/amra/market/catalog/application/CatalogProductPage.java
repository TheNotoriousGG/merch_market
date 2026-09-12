package ru.amra.market.catalog.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.AttributeType;

/** Immutable public product page without pricing, availability or storage metadata. */
public record CatalogProductPage(List<Item> items, Metadata page, String etag) {

    public CatalogProductPage {
        items = List.copyOf(items);
    }

    /** Compact product card. */
    public record Item(
            UUID id,
            String slug,
            String name,
            String shortDescription,
            @Nullable Long priceMinor,
            boolean newArrival,
            boolean onSale,
            @Nullable Integer salePercent,
            boolean featured,
            Media primaryMedia,
            Instant publishedAt,
            List<VariantOption> variantOptions) {

        public Item {
            variantOptions = List.copyOf(variantOptions);
        }
    }

    /** Public image metadata resolved through the media-delivery port. */
    public record Media(UUID id, URI url, String alt, int width, int height, int displayOrder) {}

    /** One ordered variant-defining facet exposed by active variants. */
    public record VariantOption(
            String definitionCode, String definitionName, AttributeType type, List<AttributeValue> values) {

        public VariantOption {
            values = List.copyOf(values);
        }
    }

    /** Public typed value. Label remains presentation-safe and color is optional. */
    public record AttributeValue(
            String definitionCode,
            String definitionName,
            AttributeType type,
            String valueCode,
            String label,
            @Nullable String colorHex) {}

    /** Exact page metadata used by direct page navigation. */
    public record Metadata(int page, int size, long totalElements, int totalPages) {}
}
