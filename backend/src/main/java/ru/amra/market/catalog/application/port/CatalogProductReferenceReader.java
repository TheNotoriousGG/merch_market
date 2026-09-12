package ru.amra.market.catalog.application.port;

import java.util.List;
import java.util.UUID;
import ru.amra.market.catalog.domain.ProductId;

/** Batch read boundary for public category and collection labels attached to one product. */
public interface CatalogProductReferenceReader {

    /** Returns visible references in presentation order using a bounded operation. */
    References findFor(ProductId productId);

    record References(List<CategoryRecord> categories, List<CollectionRecord> collections) {

        public References {
            categories = List.copyOf(categories);
            collections = List.copyOf(collections);
        }
    }

    record CategoryRecord(UUID id, String slug, String name) {}

    record CollectionRecord(UUID id, String slug, String name) {}
}
