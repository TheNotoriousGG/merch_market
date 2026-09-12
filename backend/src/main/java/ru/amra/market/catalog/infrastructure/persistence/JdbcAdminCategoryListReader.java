package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.AdminCategoryListCriteria;
import ru.amra.market.catalog.application.AdminCategoryView;
import ru.amra.market.catalog.application.CatalogVersionEtag;
import ru.amra.market.catalog.application.port.AdminCategoryListReader;
import ru.amra.market.catalog.domain.CategoryStatus;

/** PostgreSQL recursive-tree projection for administrative categories. */
@Repository
class JdbcAdminCategoryListReader implements AdminCategoryListReader {

    private static final String QUERY = """
            with recursive category_tree as (
                select category.*, array[category.display_order] as order_path,
                       array[category.id::text] as id_path
                from catalog_categories category
                where category.parent_id is null
                union all
                select child.*, tree.order_path || child.display_order,
                       tree.id_path || child.id::text
                from catalog_categories child
                join category_tree tree on tree.id = child.parent_id
            )
            select id, parent_id, slug, name, display_order, status, version, updated_at
            from category_tree
            where (cast(:query as text) is null or lower(name) like :query or lower(slug) like :query)
              and (cast(:status as text) is null or status = :status)
            order by order_path, id_path
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAdminCategoryListReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AdminCategoryView> findAll(AdminCategoryListCriteria criteria) {
        var parameters = new MapSqlParameterSource()
                .addValue(
                        "query",
                        criteria.query() == null ? null : "%" + criteria.query().toLowerCase(Locale.ROOT) + "%")
                .addValue(
                        "status",
                        criteria.status() == null ? null : criteria.status().name());
        return jdbc.query(QUERY, parameters, JdbcAdminCategoryListReader::row);
    }

    private static AdminCategoryView row(ResultSet resultSet, int rowNumber) throws SQLException {
        var version = resultSet.getLong("version");
        return new AdminCategoryView(
                requireNonNull(resultSet.getObject("id", UUID.class)),
                nullableUuid(resultSet, "parent_id"),
                requireNonNull(resultSet.getString("slug")),
                requireNonNull(resultSet.getString("name")),
                resultSet.getInt("display_order"),
                CategoryStatus.valueOf(requireNonNull(resultSet.getString("status"))),
                version,
                requireNonNull(resultSet.getTimestamp("updated_at")).toInstant(),
                CatalogVersionEtag.format(version));
    }

    private static @Nullable UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }
}
