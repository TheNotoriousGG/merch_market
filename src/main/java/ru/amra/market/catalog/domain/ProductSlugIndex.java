package ru.amra.market.catalog.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Validated global namespace of canonical product slugs and direct historical aliases. */
public final class ProductSlugIndex {

    private final Map<ProductSlug, SlugResolution> resolutions;

    /** Builds a namespace snapshot and rejects canonical or alias reuse across products. */
    public ProductSlugIndex(Collection<Product> products) {
        var index = new HashMap<ProductSlug, SlugResolution>();
        for (var product : products) {
            put(index, product.slug(), new SlugResolution(product.id(), product.slug(), false));
            for (var alias : product.aliases()) {
                put(index, alias, new SlugResolution(product.id(), product.slug(), true));
            }
        }
        resolutions = Map.copyOf(index);
    }

    /** Resolves either a canonical slug or alias directly to the current canonical product slug. */
    public Optional<SlugResolution> resolve(ProductSlug slug) {
        return Optional.ofNullable(resolutions.get(slug));
    }

    private static void put(Map<ProductSlug, SlugResolution> index, ProductSlug slug, SlugResolution resolution) {
        if (index.putIfAbsent(slug, resolution) != null) {
            throw new ProductInvariantViolation(
                    ProductInvariant.SLUG_NAMESPACE_CONFLICT,
                    "Canonical product slugs and aliases must be globally unique");
        }
    }

    /** Result used by the query adapter to return a body or permanent canonical redirect. */
    public record SlugResolution(ProductId productId, ProductSlug canonicalSlug, boolean alias) {}
}
