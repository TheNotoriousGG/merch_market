package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.port.VisibleCategoryReader;
import ru.amra.market.catalog.application.port.VisibleCategoryRecord;

/** PostgreSQL recursive-CTE adapter for the public category navigation. */
@Repository
class JdbcVisibleCategoryReader implements VisibleCategoryReader {

    static final String FIND_ALL_REACHABLE_SQL = """
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
            select id, parent_id, slug, name, display_order
            from visible_categories
            order by display_order, id
            """;

    private final JdbcTemplate jdbc;

    JdbcVisibleCategoryReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<VisibleCategoryRecord> findAllReachable() {
        return jdbc.query(
                FIND_ALL_REACHABLE_SQL,
                (resultSet, rowNumber) -> new VisibleCategoryRecord(
                        requireNonNull(resultSet.getObject("id", UUID.class)),
                        resultSet.getObject("parent_id", UUID.class),
                        requireNonNull(resultSet.getString("slug")),
                        requireNonNull(resultSet.getString("name")),
                        resultSet.getInt("display_order")));
    }
}
