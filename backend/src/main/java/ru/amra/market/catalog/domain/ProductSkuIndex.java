package ru.amra.market.catalog.domain;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Validated global SKU namespace across all products and variant lifecycle states. */
public final class ProductSkuIndex {

    private final Map<Sku, ProductId> ownerBySku;

    /** Builds a snapshot and rejects reuse of active or archived business SKUs. */
    public ProductSkuIndex(Collection<Product> products) {
        var index = new HashMap<Sku, ProductId>();
        for (var product : products) {
            for (var variant : product.variants()) {
                if (index.putIfAbsent(variant.sku(), product.id()) != null) {
                    throw new ProductInvariantViolation(
                            ProductInvariant.DUPLICATE_SKU, "SKU must be globally unique across products");
                }
            }
        }
        ownerBySku = Map.copyOf(index);
    }

    /** Returns the product currently owning the immutable SKU. */
    public Optional<ProductId> ownerOf(Sku sku) {
        return Optional.ofNullable(ownerBySku.get(sku));
    }
}
