package ru.amra.market.testing;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

/** Loads and removes a representative catalog distribution exclusively for SQL-plan acceptance tests. */
public final class CatalogPerformanceFixture {

    public static final int PRODUCT_COUNT = 10_000;
    public static final int VARIANT_COUNT = 40_000;

    private final JdbcTemplate jdbc;

    public CatalogPerformanceFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Loads 10k active products, four variants each and a five-level uneven category tree. */
    public Profile load() {
        var root = category(null, "perf-root", "Performance root", 0);
        var popular = category(root, "perf-popular", "Popular", 0);
        var medium = category(root, "perf-medium", "Medium", 1);
        var rare = category(root, "perf-rare", "Rare", 2);
        var levelTwo = category(root, "perf-level-two", "Level two", 3);
        var levelThree = category(levelTwo, "perf-level-three", "Level three", 0);
        var levelFour = category(levelThree, "perf-level-four", "Level four", 0);
        category(levelFour, "perf-level-five", "Level five", 0);

        jdbc.update("""
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description, status,
                    primary_category_id, published_at, version
                )
                select uuidv7(),
                       'perf-product-' || ordinal,
                       case when ordinal % 100 = 0
                            then 'Limited Edition Capsule ' || ordinal
                            else 'Amra Core Product ' || ordinal end,
                       'Representative compact card ' || ordinal,
                       'Representative full catalog description ' || ordinal,
                       'ACTIVE',
                       case when ordinal % 10 < 7 then ?
                            when ordinal % 10 < 9 then ? else ? end,
                       timestamptz '2026-08-01 00:00:00+00'
                           - ((ordinal % 365) * interval '1 day'),
                       0
                from generate_series(1, ?) ordinal
                """, popular, medium, rare, PRODUCT_COUNT);
        jdbc.update("""
                insert into catalog_product_categories (product_id, category_id)
                select id,
                       case when split_part(canonical_slug, '-', 3)::integer % 10 < 7 then ?
                            when split_part(canonical_slug, '-', 3)::integer % 10 < 9 then ? else ? end
                from catalog_products where canonical_slug like 'perf-product-%'
                """, popular, medium, rare);
        jdbc.update("""
                insert into catalog_product_media (
                    id, product_id, media_type, object_key, content_type, width, height,
                    alt_text, display_order, is_primary, version
                )
                select uuidv7(), id, 'IMAGE', 'performance/' || canonical_slug || '/primary.webp',
                       'image/webp', 1200, 1500, 'Performance ' || name, 0, true, 0
                from catalog_products where canonical_slug like 'perf-product-%'
                """);

        var colorDefinition = definition("perf_color", "Цвет", "COLOR", true, true, 0);
        var sizeDefinition = definition("perf_size", "Размер", "SIZE", true, true, 1);
        var materialDefinition = definition("perf_material", "Материал", "TEXT", false, false, 2);
        jdbc.update("""
                insert into catalog_product_characteristics (
                    product_id, attribute_definition_id, attribute_value,
                    value_label, color_hex, display_order
                )
                select id, ?, 'cotton', 'Хлопок', null, 0
                from catalog_products where canonical_slug like 'perf-product-%'
                """, materialDefinition);

        jdbc.update("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature, version
                )
                select uuidv7(),
                       product.id,
                       'PERF-' || split_part(product.canonical_slug, '-', 3) || '-' || variant.ordinal,
                       'Variant ' || variant.ordinal,
                       'ACTIVE',
                       variant.ordinal - 1,
                       'perf_color=C' || variant.ordinal || '|perf_size=S' || variant.ordinal,
                       0
                from catalog_products product
                cross join generate_series(1, 4) variant(ordinal)
                where product.canonical_slug like 'perf-product-%'
                """);
        jdbc.update("""
                insert into catalog_variant_attribute_values (
                    variant_id, attribute_definition_id, attribute_value,
                    value_label, color_hex, display_order
                )
                select id,
                       ?,
                       'C' || split_part(sku, '-', 3),
                       'Color ' || split_part(sku, '-', 3),
                       case split_part(sku, '-', 3)::integer
                           when 1 then '#191919'
                           when 2 then '#FFFFFF'
                           when 3 then '#FFD600'
                           else '#315EFB' end,
                       0
                from catalog_product_variants where sku like 'PERF-%'
                """, colorDefinition);
        jdbc.update("""
                insert into catalog_variant_attribute_values (
                    variant_id, attribute_definition_id, attribute_value,
                    value_label, color_hex, display_order
                )
                select id, ?, 'S' || split_part(sku, '-', 3),
                       'Size ' || split_part(sku, '-', 3), null, 1
                from catalog_product_variants where sku like 'PERF-%'
                """, sizeDefinition);

        var collection = requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
        jdbc.update("""
                insert into catalog_collections (
                    id, slug, name, description, status, display_order, version
                ) values (?, 'perf-selection', 'Performance selection',
                          'Representative editorial selection', 'ACTIVE', 0, 0)
                """, collection);
        jdbc.update("""
                insert into catalog_collection_products (collection_id, product_id, display_order)
                select ?, id, split_part(canonical_slug, '-', 3)::integer - 1
                from catalog_products
                where canonical_slug like 'perf-product-%'
                  and split_part(canonical_slug, '-', 3)::integer <= 2000
                """, collection);

        analyze();
        return new Profile("perf-root", "perf-rare", "perf-selection", "perf-product-100");
    }

    /** Clears catalog-owned test rows and refreshes empty-table statistics for following tests. */
    public void clear() {
        jdbc.execute("""
                truncate table
                    customer_cart_items,
                    customer_carts,
                    customer_favorites,
                    catalog_audit_events,
                    catalog_command_idempotency,
                    catalog_variant_attribute_values,
                    catalog_product_media,
                    catalog_product_characteristics,
                    catalog_collection_products,
                    catalog_product_variants,
                    catalog_product_categories,
                    catalog_product_slug_aliases,
                    catalog_collections,
                    catalog_attribute_definitions,
                    catalog_products,
                    catalog_categories
                """);
        analyze();
    }

    private UUID category(@Nullable UUID parentId, String slug, String name, int order) {
        var id = requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
        jdbc.update("""
                insert into catalog_categories (
                    id, parent_id, slug, name, display_order, status, version
                ) values (?, ?, ?, ?, ?, 'ACTIVE', 0)
                """, id, parentId, slug, name, order);
        return id;
    }

    private UUID definition(
            String code, String name, String type, boolean filterable, boolean variantDefining, int order) {
        var id = requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
        jdbc.update("""
                insert into catalog_attribute_definitions (
                    id, code, display_name, attribute_type, filterable,
                    variant_defining, display_order, version
                ) values (?, ?, ?, ?, ?, ?, ?, 0)
                """, id, code, name, type, filterable, variantDefining, order);
        return id;
    }

    private void analyze() {
        jdbc.execute("""
                analyze catalog_categories, catalog_products, catalog_product_categories,
                        catalog_product_media, catalog_product_variants,
                        catalog_variant_attribute_values, catalog_attribute_definitions,
                        catalog_collections, catalog_collection_products
                """);
    }

    /** Stable identifiers needed by acceptance queries. */
    public record Profile(String rootCategory, String rareCategory, String collection, String detailSlug) {}
}
