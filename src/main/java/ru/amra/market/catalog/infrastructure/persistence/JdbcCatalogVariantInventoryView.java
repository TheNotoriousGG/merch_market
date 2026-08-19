package ru.amra.market.catalog.infrastructure.persistence;

import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.integration.CatalogVariantInventoryView;

/** Bounded catalog projection exposing only active variant identities to inventory. */
@Repository
class JdbcCatalogVariantInventoryView implements CatalogVariantInventoryView {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcCatalogVariantInventoryView(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> findActiveVariantIds(Set<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.queryForList("""
                select variant.id
                from catalog_product_variants variant
                join catalog_products product on product.id = variant.product_id
                where variant.id in (:ids)
                  and variant.status = 'ACTIVE'
                  and product.status = 'ACTIVE'
                """, java.util.Map.of("ids", variantIds), UUID.class));
    }
}
