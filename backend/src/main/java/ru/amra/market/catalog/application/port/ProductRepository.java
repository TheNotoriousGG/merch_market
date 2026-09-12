package ru.amra.market.catalog.application.port;

import java.util.Optional;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductSlug;

/** Persistence boundary for complete product aggregates. */
public interface ProductRepository {

    /** Inserts or optimistically updates a complete product aggregate. */
    Product save(Product product);

    /** Loads a complete aggregate by identity using a bounded query plan. */
    Optional<Product> findById(ProductId id);

    /** Resolves a canonical or historical slug directly to the current aggregate. */
    Optional<ProductLookup> findBySlug(ProductSlug slug);

    /** Permanently deletes an aggregate and all database-owned children. */
    void delete(ProductId id);
}
