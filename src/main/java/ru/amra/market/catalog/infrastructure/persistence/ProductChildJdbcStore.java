package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.domain.AttributeType;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.MediaId;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductInvariant;
import ru.amra.market.catalog.domain.ProductInvariantViolation;
import ru.amra.market.catalog.domain.ProductMedia;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductVariant;
import ru.amra.market.catalog.domain.Sku;
import ru.amra.market.catalog.domain.VariantId;
import ru.amra.market.catalog.domain.VariantStatus;

@Component
class ProductChildJdbcStore {

    private final JdbcTemplate jdbc;

    ProductChildJdbcStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void assertSlugNamespace(Product product) {
        if (exists("""
                select exists(
                    select 1
                    from catalog_product_slug_aliases
                    where lower(alias_slug) = lower(?) and product_id <> ?
                )
                """, product.slug().value(), product.id().value())) {
            throw slugConflict();
        }
        for (var alias : product.aliases()) {
            if (exists(
                    "select exists(select 1 from catalog_products where lower(canonical_slug) = lower(?) and id <> ?)",
                    alias.value(),
                    product.id().value())) {
                throw slugConflict();
            }
        }
    }

    void synchronize(Product product) {
        synchronizeAliases(product);
        synchronizeCategories(product);
        synchronizeCharacteristics(product);
        synchronizeVariants(product);
        synchronizeMedia(product);
        synchronizeCollections(product);
    }

    ProductChildState load(ProductId productId) {
        var id = productId.value();
        var aliases = jdbc.query(
                "select alias_slug from catalog_product_slug_aliases where product_id = ? order by alias_slug",
                (result, row) -> new ProductSlug(result.getString("alias_slug")),
                id);
        var categories = jdbc.query(
                "select category_id from catalog_product_categories where product_id = ? order by category_id",
                (result, row) -> new CategoryId(result.getObject("category_id", UUID.class)),
                id);
        var collections = jdbc.query(
                "select collection_id from catalog_collection_products where product_id = ? order by collection_id",
                (result, row) -> new CollectionId(result.getObject("collection_id", UUID.class)),
                id);
        var characteristics = jdbc.query("""
                select d.code, d.display_name, d.attribute_type, d.variant_defining,
                       c.attribute_value, c.display_order
                from catalog_product_characteristics c
                join catalog_attribute_definitions d on d.id = c.attribute_definition_id
                where c.product_id = ?
                order by c.display_order, d.code
                """, (result, row) -> attribute(result), id);
        var variantRows = jdbc.query("""
                select id, sku, label, status, display_order, version
                from catalog_product_variants
                where product_id = ?
                order by display_order, sku
                """, (result, row) -> variantRow(result), id);
        var attributesByVariant = loadVariantAttributes(id);
        var variants = variantRows.stream()
                .map(row -> row.toDomain(attributesByVariant.getOrDefault(row.id(), List.of())))
                .toList();
        var media = jdbc.query("""
                select id, variant_id, object_key, content_type, width, height,
                       alt_text, display_order, is_primary, version
                from catalog_product_media
                where product_id = ?
                order by display_order, id
                """, (result, row) -> media(result), id);
        return new ProductChildState(
                Set.copyOf(aliases), Set.copyOf(categories), Set.copyOf(collections), characteristics, variants, media);
    }

    Optional<ProductId> findProductIdByAlias(ProductSlug slug) {
        var ids = jdbc.query(
                "select product_id from catalog_product_slug_aliases where lower(alias_slug) = lower(?)",
                (result, row) -> new ProductId(result.getObject("product_id", UUID.class)),
                slug.value());
        return ids.stream().findFirst();
    }

    private void synchronizeAliases(Product product) {
        for (var alias : product.aliases()) {
            jdbc.update("""
                    insert into catalog_product_slug_aliases (alias_slug, product_id)
                    values (?, ?)
                    on conflict (alias_slug) do nothing
                    """, alias.value(), product.id().value());
            var owner = requireNonNull(jdbc.queryForObject(
                    "select product_id from catalog_product_slug_aliases where alias_slug = ?",
                    UUID.class,
                    alias.value()));
            if (!owner.equals(product.id().value())) {
                throw slugConflict();
            }
        }
    }

    private void synchronizeCategories(Product product) {
        jdbc.update(
                "delete from catalog_product_categories where product_id = ?",
                product.id().value());
        for (var categoryId : product.categoryIds()) {
            jdbc.update(
                    "insert into catalog_product_categories (product_id, category_id) values (?, ?)",
                    product.id().value(),
                    categoryId.value());
        }
    }

    private void synchronizeCharacteristics(Product product) {
        jdbc.update(
                "delete from catalog_product_characteristics where product_id = ?",
                product.id().value());
        for (var attribute : product.characteristics()) {
            var definitionId = definitionId(attribute);
            jdbc.update("""
                    insert into catalog_product_characteristics (
                        product_id, attribute_definition_id, attribute_value, display_order
                    ) values (?, ?, ?, ?)
                    """, product.id().value(), definitionId, attribute.value(), attribute.displayOrder());
        }
    }

    private void synchronizeVariants(Product product) {
        for (var variant : product.variants()) {
            var stored = findVariant(variant.id());
            if (stored.isEmpty()) {
                jdbc.update(
                        """
                        insert into catalog_product_variants (
                            id, product_id, sku, label, status, display_order, defining_signature, version
                        ) values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        variant.id().value(),
                        product.id().value(),
                        variant.sku().value(),
                        variant.label(),
                        variant.status().name(),
                        variant.displayOrder(),
                        variant.definingCombination(),
                        variant.version());
            } else {
                updateVariant(product.id(), variant, stored.orElseThrow());
            }
            synchronizeVariantAttributes(variant);
        }
    }

    private void updateVariant(ProductId productId, ProductVariant variant, StoredVariant stored) {
        if (stored.matches(productId, variant)) {
            return;
        }
        if (variant.version() != stored.version() + 1) {
            throw stale("Variant", variant.id().value(), variant.version(), stored.version());
        }
        var updated = jdbc.update(
                """
                update catalog_product_variants
                set label = ?, status = ?, display_order = ?, defining_signature = ?,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                where id = ? and product_id = ? and version = ? and sku = ?
                """,
                variant.label(),
                variant.status().name(),
                variant.displayOrder(),
                variant.definingCombination(),
                variant.id().value(),
                productId.value(),
                stored.version(),
                variant.sku().value());
        if (updated != 1) {
            throw stale("Variant", variant.id().value(), variant.version(), stored.version());
        }
    }

    private void synchronizeVariantAttributes(ProductVariant variant) {
        jdbc.update(
                "delete from catalog_variant_attribute_values where variant_id = ?",
                variant.id().value());
        for (var attribute : variant.attributes()) {
            jdbc.update(
                    """
                    insert into catalog_variant_attribute_values (
                        variant_id, attribute_definition_id, attribute_value, display_order
                    ) values (?, ?, ?, ?)
                    """, variant.id().value(), definitionId(attribute), attribute.value(), attribute.displayOrder());
        }
    }

    private void synchronizeMedia(Product product) {
        for (var media : product.media()) {
            var stored = findMedia(media.id());
            if (stored.isEmpty()) {
                jdbc.update(
                        """
                        insert into catalog_product_media (
                            id, product_id, variant_id, media_type, object_key, content_type,
                            width, height, alt_text, display_order, is_primary, version
                        ) values (?, ?, ?, 'IMAGE', ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        media.id().value(),
                        product.id().value(),
                        media.variantId().map(VariantId::value).orElse(null),
                        media.objectKey(),
                        media.contentType(),
                        media.width(),
                        media.height(),
                        media.alt(),
                        media.displayOrder(),
                        media.primary(),
                        media.version());
            } else if (!stored.orElseThrow().matches(product.id(), media)) {
                throw stale(
                        "Media",
                        media.id().value(),
                        media.version(),
                        stored.orElseThrow().version());
            }
        }
    }

    private void synchronizeCollections(Product product) {
        var stored = new HashSet<>(jdbc.query(
                "select collection_id from catalog_collection_products where product_id = ?",
                (result, row) -> new CollectionId(result.getObject("collection_id", UUID.class)),
                product.id().value()));
        for (var removed : stored) {
            if (!product.collectionIds().contains(removed)) {
                jdbc.update(
                        "delete from catalog_collection_products where collection_id = ? and product_id = ?",
                        removed.value(),
                        product.id().value());
            }
        }
        for (var added : product.collectionIds()) {
            if (!stored.contains(added)) {
                jdbc.queryForObject(
                        "select id from catalog_collections where id = ? for update", UUID.class, added.value());
                jdbc.update("""
                        insert into catalog_collection_products (collection_id, product_id, display_order)
                        select ?, ?, coalesce(max(display_order), -1) + 1
                        from catalog_collection_products where collection_id = ?
                        """, added.value(), product.id().value(), added.value());
            }
        }
    }

    private UUID definitionId(AttributeValue attribute) {
        var stored = jdbc.query(
                """
                select id, display_name, attribute_type, variant_defining
                from catalog_attribute_definitions where code = ?
                """,
                (result, row) -> new StoredDefinition(
                        result.getObject("id", UUID.class),
                        result.getString("display_name"),
                        AttributeType.valueOf(result.getString("attribute_type")),
                        result.getBoolean("variant_defining")),
                attribute.code());
        if (!stored.isEmpty()) {
            var definition = stored.getFirst();
            if (!definition.matches(attribute)) {
                throw new ProductInvariantViolation(
                        ProductInvariant.INVALID_ATTRIBUTE,
                        "Attribute code is already bound to incompatible definition metadata");
            }
            return definition.id();
        }
        var id = requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
        jdbc.update(
                """
                insert into catalog_attribute_definitions (
                    id, code, display_name, attribute_type, filterable,
                    variant_defining, display_order
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                attribute.code(),
                attribute.displayName(),
                attribute.type().name(),
                attribute.type() == AttributeType.COLOR || attribute.type() == AttributeType.SIZE,
                attribute.variantDefining(),
                attribute.displayOrder());
        return id;
    }

    private Map<UUID, List<AttributeValue>> loadVariantAttributes(UUID productId) {
        var result = new HashMap<UUID, List<AttributeValue>>();
        jdbc.query(
                """
                select v.id as variant_id, d.code, d.display_name, d.attribute_type,
                       d.variant_defining, a.attribute_value, a.display_order
                from catalog_variant_attribute_values a
                join catalog_product_variants v on v.id = a.variant_id
                join catalog_attribute_definitions d on d.id = a.attribute_definition_id
                where v.product_id = ?
                order by v.id, a.display_order, d.code
                """,
                resultSet -> {
                    var variantId = resultSet.getObject("variant_id", UUID.class);
                    result.computeIfAbsent(variantId, ignored -> new ArrayList<>())
                            .add(attribute(resultSet));
                },
                productId);
        return result;
    }

    private Optional<StoredVariant> findVariant(VariantId id) {
        return jdbc
                .query(
                        """
                        select product_id, sku, label, status, display_order, defining_signature, version
                        from catalog_product_variants where id = ?
                        """,
                        (result, row) -> new StoredVariant(
                                result.getObject("product_id", UUID.class),
                                result.getString("sku"),
                                result.getString("label"),
                                result.getString("status"),
                                result.getInt("display_order"),
                                result.getString("defining_signature"),
                                result.getLong("version")),
                        id.value())
                .stream()
                .findFirst();
    }

    private Optional<StoredMedia> findMedia(MediaId id) {
        return jdbc.query("""
                        select product_id, variant_id, object_key, content_type, width, height,
                               alt_text, display_order, is_primary, version
                        from catalog_product_media where id = ?
                        """, (result, row) -> storedMedia(result), id.value()).stream()
                .findFirst();
    }

    private boolean exists(String sql, Object... arguments) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, arguments));
    }

    private static AttributeValue attribute(ResultSet result) throws SQLException {
        return new AttributeValue(
                result.getString("code"),
                result.getString("display_name"),
                AttributeType.valueOf(result.getString("attribute_type")),
                result.getString("attribute_value"),
                result.getBoolean("variant_defining"),
                result.getInt("display_order"));
    }

    private static VariantRow variantRow(ResultSet result) throws SQLException {
        return new VariantRow(
                result.getObject("id", UUID.class),
                result.getString("sku"),
                result.getString("label"),
                VariantStatus.valueOf(result.getString("status")),
                result.getInt("display_order"),
                result.getLong("version"));
    }

    private static ProductMedia media(ResultSet result) throws SQLException {
        var variantUuid = result.getObject("variant_id", UUID.class);
        var variantId = variantUuid == null ? null : new VariantId(variantUuid);
        return ProductMedia.restoreImage(
                new MediaId(result.getObject("id", UUID.class)),
                variantId,
                result.getString("object_key"),
                result.getString("content_type"),
                result.getInt("width"),
                result.getInt("height"),
                result.getString("alt_text"),
                result.getInt("display_order"),
                result.getBoolean("is_primary"),
                result.getLong("version"));
    }

    private static StoredMedia storedMedia(ResultSet result) throws SQLException {
        return new StoredMedia(
                result.getObject("product_id", UUID.class),
                result.getObject("variant_id", UUID.class),
                result.getString("object_key"),
                result.getString("content_type"),
                result.getInt("width"),
                result.getInt("height"),
                result.getString("alt_text"),
                result.getInt("display_order"),
                result.getBoolean("is_primary"),
                result.getLong("version"));
    }

    private static ProductInvariantViolation slugConflict() {
        return new ProductInvariantViolation(
                ProductInvariant.SLUG_NAMESPACE_CONFLICT,
                "Canonical product slugs and aliases must be globally unique");
    }

    private static ConcurrentCatalogModificationException stale(
            String resource, UUID id, long candidateVersion, long storedVersion) {
        return new ConcurrentCatalogModificationException(resource + ' ' + id + " version " + candidateVersion
                + " does not follow stored version " + storedVersion);
    }

    private record StoredDefinition(UUID id, String displayName, AttributeType type, boolean variantDefining) {
        boolean matches(AttributeValue attribute) {
            return displayName.equals(attribute.displayName())
                    && type == attribute.type()
                    && variantDefining == attribute.variantDefining();
        }
    }

    private record VariantRow(UUID id, String sku, String label, VariantStatus status, int displayOrder, long version) {
        ProductVariant toDomain(List<AttributeValue> attributes) {
            return ProductVariant.restore(
                    new VariantId(id), new Sku(sku), label, status, displayOrder, attributes, version);
        }
    }

    private record StoredVariant(
            UUID productId, String sku, String label, String status, int displayOrder, String signature, long version) {
        boolean matches(ProductId owner, ProductVariant variant) {
            return productId.equals(owner.value())
                    && sku.equals(variant.sku().value())
                    && label.equals(variant.label())
                    && status.equals(variant.status().name())
                    && displayOrder == variant.displayOrder()
                    && signature.equals(variant.definingCombination())
                    && version == variant.version();
        }
    }

    private record StoredMedia(
            UUID productId,
            @Nullable UUID variantId,
            String objectKey,
            String contentType,
            int width,
            int height,
            String alt,
            int displayOrder,
            boolean primary,
            long version) {
        boolean matches(ProductId owner, ProductMedia media) {
            return productId.equals(owner.value())
                    && java.util.Objects.equals(
                            variantId, media.variantId().map(VariantId::value).orElse(null))
                    && objectKey.equals(media.objectKey())
                    && contentType.equals(media.contentType())
                    && width == media.width()
                    && height == media.height()
                    && alt.equals(media.alt())
                    && displayOrder == media.displayOrder()
                    && primary == media.primary()
                    && version == media.version();
        }
    }
}
