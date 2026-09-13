package ru.amra.market.catalog.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.EditorialCollectionRepository;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.CollectionSlug;
import ru.amra.market.catalog.domain.CollectionStatus;
import ru.amra.market.catalog.domain.EditorialCollection;
import ru.amra.market.catalog.domain.ProductId;

/** JDBC adapter for the small collection aggregate and ordered membership. */
@Repository
class JdbcEditorialCollectionRepository implements EditorialCollectionRepository {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    JdbcEditorialCollectionRepository(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public EditorialCollection save(EditorialCollection collection) {
        var current = findById(collection.id());
        if (current.isEmpty()) {
            if (collection.version() != 0) {
                throw stale(collection, -1);
            }
            jdbc.update(
                    """
                    insert into catalog_collections (
                        id, slug, name, description, status, display_order, version
                    ) values (?, ?, ?, ?, ?, ?, 0)
                    """,
                    collection.id().value(),
                    collection.slug().value(),
                    collection.name(),
                    collection.description(),
                    collection.status().name(),
                    collection.displayOrder());
            synchronizeProducts(List.of(), collection);
            return findById(collection.id()).orElseThrow();
        }
        var stored = current.orElseThrow();
        if (same(stored, collection)) {
            return stored;
        }
        if (collection.version() != stored.version() + 1) {
            throw stale(collection, stored.version());
        }
        var updated = jdbc.update(
                """
                update catalog_collections
                set slug = ?, name = ?, description = ?, status = ?, display_order = ?,
                    updated_at = current_timestamp, version = version + 1
                where id = ? and version = ?
                """,
                collection.slug().value(),
                collection.name(),
                collection.description(),
                collection.status().name(),
                collection.displayOrder(),
                collection.id().value(),
                stored.version());
        if (updated != 1) {
            throw stale(collection, stored.version());
        }
        synchronizeProducts(stored.productIds(), collection);
        return findById(collection.id()).orElseThrow();
    }

    @Override
    public Optional<EditorialCollection> findById(CollectionId id) {
        var row = jdbc
                .query(
                        """
                        select id, slug, name, description, status, display_order, version
                        from catalog_collections where id = ?
                        """,
                        (result, rowNumber) -> new CollectionRow(
                                result.getObject("id", UUID.class),
                                result.getString("slug"),
                                result.getString("name"),
                                result.getString("description"),
                                result.getString("status"),
                                result.getInt("display_order"),
                                result.getLong("version")),
                        id.value())
                .stream()
                .findFirst();
        return row.map(stored -> EditorialCollection.restore(
                new CollectionId(stored.id()),
                new CollectionSlug(stored.slug()),
                stored.name(),
                stored.description(),
                CollectionStatus.valueOf(stored.status()),
                stored.displayOrder(),
                products(id),
                stored.version()));
    }

    @Override
    public List<EditorialCollection> findAll() {
        return jdbc
                .query(
                        "select id from catalog_collections order by display_order, id",
                        (result, row) -> new CollectionId(result.getObject("id", UUID.class)))
                .stream()
                .map(id -> findById(id).orElseThrow())
                .toList();
    }

    private List<ProductId> products(CollectionId id) {
        return jdbc.query("""
                select product_id from catalog_collection_products
                where collection_id = ? order by display_order
                """, (result, row) -> new ProductId(result.getObject("product_id", UUID.class)), id.value());
    }

    private void synchronizeProducts(List<ProductId> previous, EditorialCollection collection) {
        jdbc.update(
                "delete from catalog_collection_products where collection_id = ?",
                collection.id().value());
        for (var index = 0; index < collection.productIds().size(); index++) {
            jdbc.update(
                    """
                    insert into catalog_collection_products (collection_id, product_id, display_order)
                    values (?, ?, ?)
                    """,
                    collection.id().value(),
                    collection.productIds().get(index).value(),
                    index);
        }
        var affected = new java.util.HashSet<>(previous);
        affected.addAll(collection.productIds());
        var productVersionChanged = false;
        for (var productId : affected) {
            if (previous.contains(productId) != collection.productIds().contains(productId)) {
                jdbc.update("""
                        update catalog_products
                        set updated_at = clock_timestamp(), version = version + 1
                        where id = ?
                        """, productId.value());
                productVersionChanged = true;
            }
        }
        if (productVersionChanged) {
            entityManager.clear();
        }
    }

    private static boolean same(EditorialCollection left, EditorialCollection right) {
        return left.slug().equals(right.slug())
                && left.name().equals(right.name())
                && left.description().equals(right.description())
                && left.status() == right.status()
                && left.displayOrder() == right.displayOrder()
                && left.productIds().equals(right.productIds())
                && left.version() == right.version();
    }

    private static ConcurrentCatalogModificationException stale(EditorialCollection collection, long storedVersion) {
        return new ConcurrentCatalogModificationException("Collection " + collection.id() + " version "
                + collection.version() + " does not follow stored version " + storedVersion);
    }

    private record CollectionRow(
            UUID id, String slug, String name, String description, String status, int displayOrder, long version) {}
}
