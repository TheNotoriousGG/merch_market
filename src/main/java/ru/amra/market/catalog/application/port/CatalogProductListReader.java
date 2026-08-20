package ru.amra.market.catalog.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.application.CatalogProductListCriteria;
import ru.amra.market.catalog.domain.AttributeType;

/** Read-optimized persistence boundary for the public product grid. */
public interface CatalogProductListReader {

    /** Returns one exact page and total count using a bounded number of database queries. */
    Result find(CatalogProductListCriteria criteria, Instant newAfter);

    record Result(List<ProductRecord> products, long totalElements) {

        public Result {
            products = List.copyOf(products);
        }
    }

    record ProductRecord(
            UUID id,
            String slug,
            String name,
            String shortDescription,
            @Nullable Long priceMinor,
            Instant publishedAt,
            MediaRecord primaryMedia,
            List<VariantOptionRecord> variantOptions) {

        public ProductRecord {
            variantOptions = List.copyOf(variantOptions);
        }
    }

    record MediaRecord(UUID id, String objectKey, String alt, int width, int height, int displayOrder) {}

    record VariantOptionRecord(
            String definitionCode, String definitionName, AttributeType type, List<AttributeValueRecord> values) {

        public VariantOptionRecord {
            values = List.copyOf(values);
        }
    }

    record AttributeValueRecord(
            String definitionCode, String definitionName, AttributeType type, String valueCode, String label) {}
}
