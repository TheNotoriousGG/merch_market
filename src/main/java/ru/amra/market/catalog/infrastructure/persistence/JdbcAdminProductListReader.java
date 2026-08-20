package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.AdminProductListCriteria;
import ru.amra.market.catalog.application.AdminProductListPage;
import ru.amra.market.catalog.application.port.AdminProductListReader;
import ru.amra.market.catalog.domain.ProductStatus;

/** PostgreSQL projection for the dense administrative product table. */
@Repository
class JdbcAdminProductListReader implements AdminProductListReader {

    private static final String RELATION = """
            from catalog_products product
            where (:query is null
                   or lower(product.name) like :query
                   or lower(product.canonical_slug) like :query)
              and (:status is null or product.status = :status)
              and (:categoryId is null or exists (
                    select 1
                    from catalog_product_categories assignment
                    where assignment.product_id = product.id
                      and assignment.category_id = :categoryId
              ))
            """;

    private static final String PROJECTION = """
            select product.id,
                   product.canonical_slug,
                   product.name,
                   product.status,
                   product.primary_category_id,
                   (select count(*) from catalog_product_variants variant
                    where variant.product_id = product.id) as variant_count,
                   (select count(*) from catalog_product_media media
                    where media.product_id = product.id) as media_count,
                   exists (select 1 from catalog_product_media media
                           where media.product_id = product.id and media.is_primary) as has_primary_media,
                   product.version,
                   product.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAdminProductListReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AdminProductListPage findPage(AdminProductListCriteria criteria) {
        var parameters = parameters(criteria);
        var total = requireNonNull(jdbc.queryForObject("select count(*) " + RELATION, parameters, Long.class));
        if (total == 0) {
            return new AdminProductListPage(List.of(), criteria.page(), criteria.size(), 0);
        }
        parameters.addValue("limit", criteria.size()).addValue("offset", criteria.offset());
        var items = jdbc.query(
                PROJECTION + RELATION + orderBy(criteria.sort()) + " limit :limit offset :offset",
                parameters,
                JdbcAdminProductListReader::row);
        return new AdminProductListPage(items, criteria.page(), criteria.size(), total);
    }

    private static MapSqlParameterSource parameters(AdminProductListCriteria criteria) {
        return new MapSqlParameterSource()
                .addValue(
                        "query",
                        criteria.query() == null
                                ? null
                                : "%" + criteria.query().toLowerCase(java.util.Locale.ROOT) + "%")
                .addValue(
                        "status",
                        criteria.status() == null ? null : criteria.status().name())
                .addValue(
                        "categoryId",
                        criteria.categoryId() == null
                                ? null
                                : criteria.categoryId().value());
    }

    private static String orderBy(AdminProductListCriteria.Sort sort) {
        return switch (sort) {
            case UPDATED_DESC -> " order by product.updated_at desc, product.id desc";
            case UPDATED_ASC -> " order by product.updated_at, product.id";
            case NAME_ASC -> " order by lower(product.name), product.id";
            case NAME_DESC -> " order by lower(product.name) desc, product.id desc";
        };
    }

    private static AdminProductListPage.Item row(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AdminProductListPage.Item(
                requireNonNull(resultSet.getObject("id", UUID.class)),
                requireNonNull(resultSet.getString("canonical_slug")),
                requireNonNull(resultSet.getString("name")),
                ProductStatus.valueOf(requireNonNull(resultSet.getString("status"))),
                requireNonNull(resultSet.getObject("primary_category_id", UUID.class)),
                resultSet.getInt("variant_count"),
                resultSet.getInt("media_count"),
                resultSet.getBoolean("has_primary_media"),
                resultSet.getLong("version"),
                requireNonNull(resultSet.getTimestamp("updated_at")).toInstant());
    }
}
