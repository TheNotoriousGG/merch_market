package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.port.CatalogProductReferenceReader;
import ru.amra.market.catalog.domain.ProductId;

/** Bounded PostgreSQL projections for visible category and collection product references. */
@Repository
class JdbcCatalogProductReferenceReader implements CatalogProductReferenceReader {

    private static final String CATEGORIES = """
            with recursive visible_categories as (
                select id, parent_id, slug, name, display_order
                from catalog_categories
                where parent_id is null and status = 'ACTIVE'
                union all
                select child.id, child.parent_id, child.slug, child.name, child.display_order
                from catalog_categories child
                join visible_categories parent on child.parent_id = parent.id
                where child.status = 'ACTIVE'
            )
            select category.id, category.slug, category.name
            from catalog_product_categories assignment
            join visible_categories category on category.id = assignment.category_id
            where assignment.product_id = ?
            order by category.display_order, category.id
            """;

    private static final String COLLECTIONS = """
            select collection.id, collection.slug, collection.name
            from catalog_collection_products membership
            join catalog_collections collection on collection.id = membership.collection_id
            where membership.product_id = ? and collection.status = 'ACTIVE'
            order by membership.display_order, collection.id
            """;

    private final JdbcTemplate jdbc;

    JdbcCatalogProductReferenceReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public References findFor(ProductId productId) {
        var categories = jdbc.query(
                CATEGORIES,
                (resultSet, rowNumber) -> new CategoryRecord(
                        requireNonNull(resultSet.getObject("id", UUID.class)),
                        requireNonNull(resultSet.getString("slug")),
                        requireNonNull(resultSet.getString("name"))),
                productId.value());
        var collections = jdbc.query(
                COLLECTIONS,
                (resultSet, rowNumber) -> new CollectionRecord(
                        requireNonNull(resultSet.getObject("id", UUID.class)),
                        requireNonNull(resultSet.getString("slug")),
                        requireNonNull(resultSet.getString("name"))),
                productId.value());
        return new References(categories, collections);
    }
}
