package ru.amra.market.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.integration.CatalogVariantInventoryView;

/** Bounded catalog projection exposing only active variant identities to inventory. */
@Repository
class JdbcCatalogVariantInventoryView implements CatalogVariantInventoryView {
    private static final String WAREHOUSE_VARIANTS = String.join(
            "\n",
            "select product.id as product_id, product.name as product_name, product.status as product_status,",
            "       variant.id as variant_id, variant.sku, variant.label as variant_label",
            "from catalog_product_variants variant",
            "join catalog_products product on product.id = variant.product_id",
            "where variant.status = 'ACTIVE' and product.status <> 'ARCHIVED'",
            "order by lower(product.name), variant.display_order, variant.id");

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

    @Override
    public Set<UUID> findReceivableVariantIds(Set<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.queryForList("""
                select variant.id
                from catalog_product_variants variant
                join catalog_products product on product.id = variant.product_id
                where variant.id in (:ids)
                  and variant.status = 'ACTIVE'
                  and product.status <> 'ARCHIVED'
                """, java.util.Map.of("ids", variantIds), UUID.class));
    }

    @Override
    public List<WarehouseVariant> listWarehouseVariants() {
        return jdbc.getJdbcTemplate()
                .query(
                        WAREHOUSE_VARIANTS,
                        (result, row) -> new WarehouseVariant(
                                result.getObject("product_id", UUID.class),
                                result.getString("product_name"),
                                result.getString("product_status"),
                                result.getObject("variant_id", UUID.class),
                                result.getString("sku"),
                                result.getString("variant_label")));
    }
}
