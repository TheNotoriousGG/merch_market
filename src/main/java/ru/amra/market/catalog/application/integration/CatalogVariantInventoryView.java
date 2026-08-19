package ru.amra.market.catalog.application.integration;

import java.util.Set;
import java.util.UUID;

/** Catalog-owned batch view used by inventory to validate stable active variant references. */
public interface CatalogVariantInventoryView {

    /**
     * Returns the subset of supplied identifiers that currently belong to active catalog variants.
     *
     * <p>Unknown, draft-product and archived identifiers are omitted. The implementation must remain bounded and must
     * not expose catalog persistence objects.
     */
    Set<UUID> findActiveVariantIds(Set<UUID> variantIds);
}
