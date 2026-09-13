package ru.amra.market.catalog.application.integration;

import java.util.List;
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

    /** Returns active variants whose product has not been archived and may receive physical stock. */
    Set<UUID> findReceivableVariantIds(Set<UUID> variantIds);

    /** Lists catalog identities needed by the protected warehouse workspace. */
    List<WarehouseVariant> listWarehouseVariants();

    /** Catalog-owned projection without persistence objects or stock semantics. */
    record WarehouseVariant(
            UUID productId,
            String productName,
            String productStatus,
            UUID variantId,
            String sku,
            String variantLabel) {}
}
