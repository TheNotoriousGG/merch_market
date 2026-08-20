package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.CatalogProductListCriteria;
import ru.amra.market.catalog.application.CatalogProductSort;
import ru.amra.market.catalog.application.port.CatalogProductListReader;
import ru.amra.market.catalog.domain.AttributeType;

/** PostgreSQL FTS/filter/page adapter for compact public product cards. */
@Repository
class JdbcCatalogProductListReader implements CatalogProductListReader {

    private static final String CATEGORY_SCOPE = """
            with recursive requested_categories as (
                select id
                from catalog_categories
                where slug = :category and status = 'ACTIVE'
                union all
                select child.id
                from catalog_categories child
                join requested_categories parent on child.parent_id = parent.id
                where child.status = 'ACTIVE'
            )
            """;

    private static final String PRODUCT_RELATION = """
            from catalog_products product
            join catalog_product_media media
              on media.product_id = product.id and media.is_primary
            where product.status = 'ACTIVE'
            """;

    private static final String PAGE_PROJECTION = """
            select product.id,
                   product.canonical_slug,
                   product.name,
                   product.short_description,
                   product.price_minor,
                   product.published_at,
                   media.id as media_id,
                   media.object_key,
                   media.alt_text,
                   media.width,
                   media.height,
                   media.display_order as media_display_order
            """;

    private static final String VARIANT_OPTIONS = """
            select variant.product_id,
                   definition.code,
                   definition.display_name,
                   definition.attribute_type,
                   value.attribute_value,
                   definition.display_order as definition_order,
                   value.display_order as value_order
            from catalog_product_variants variant
            join catalog_variant_attribute_values value on value.variant_id = variant.id
            join catalog_attribute_definitions definition on definition.id = value.attribute_definition_id
            where variant.product_id in (:productIds)
              and variant.status = 'ACTIVE'
              and definition.variant_defining
              and definition.attribute_type in ('COLOR', 'SIZE', 'DIMENSION')
            order by variant.product_id,
                     definition.display_order,
                     definition.code,
                     value.display_order,
                     value.attribute_value
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCatalogProductListReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Result find(CatalogProductListCriteria criteria, Instant newAfter) {
        requireNonNull(newAfter);
        var query = query(criteria);
        var total = requireNonNull(jdbc.queryForObject(
                query.cte() + "select count(*) " + query.relation(), query.parameters(), Long.class));
        if (total == 0) {
            return new Result(List.of(), 0);
        }

        query.parameters()
                .addValue("limit", criteria.size())
                .addValue("offset", Math.multiplyExact((long) criteria.page(), criteria.size()));
        var baseProducts = jdbc.query(
                query.cte() + PAGE_PROJECTION + query.relation() + query.orderBy() + " limit :limit offset :offset",
                query.parameters(),
                JdbcCatalogProductListReader::productRow);
        if (baseProducts.isEmpty()) {
            return new Result(List.of(), total);
        }

        var options =
                variantOptions(baseProducts.stream().map(ProductBaseRow::id).toList());
        var products = baseProducts.stream()
                .map(product -> new ProductRecord(
                        product.id(),
                        product.slug(),
                        product.name(),
                        product.shortDescription(),
                        product.priceMinor(),
                        product.publishedAt(),
                        product.media(),
                        options.getOrDefault(product.id(), List.of())))
                .toList();
        return new Result(products, total);
    }

    private Map<UUID, List<VariantOptionRecord>> variantOptions(List<UUID> productIds) {
        var grouped = new LinkedHashMap<UUID, LinkedHashMap<String, OptionAccumulator>>();
        jdbc.query(VARIANT_OPTIONS, new MapSqlParameterSource("productIds", productIds), resultSet -> {
            var productId = requireNonNull(resultSet.getObject("product_id", UUID.class));
            var code = requireNonNull(resultSet.getString("code"));
            var type = AttributeType.valueOf(requireNonNull(resultSet.getString("attribute_type")));
            var name = requireNonNull(resultSet.getString("display_name"));
            var value = requireNonNull(resultSet.getString("attribute_value"));
            grouped.computeIfAbsent(productId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(code, ignored -> new OptionAccumulator(code, name, type))
                    .add(value);
        });

        var result = new LinkedHashMap<UUID, List<VariantOptionRecord>>();
        grouped.forEach((productId, definitions) -> result.put(
                productId,
                definitions.values().stream().map(OptionAccumulator::toRecord).toList()));
        return Map.copyOf(result);
    }

    private static ProductBaseRow productRow(ResultSet resultSet, int rowNumber) throws SQLException {
        var media = new MediaRecord(
                requireNonNull(resultSet.getObject("media_id", UUID.class)),
                requireNonNull(resultSet.getString("object_key")),
                requireNonNull(resultSet.getString("alt_text")),
                resultSet.getInt("width"),
                resultSet.getInt("height"),
                resultSet.getInt("media_display_order"));
        return new ProductBaseRow(
                requireNonNull(resultSet.getObject("id", UUID.class)),
                requireNonNull(resultSet.getString("canonical_slug")),
                requireNonNull(resultSet.getString("name")),
                requireNonNull(resultSet.getString("short_description")),
                resultSet.getObject("price_minor", Long.class),
                requireNonNull(resultSet.getTimestamp("published_at")).toInstant(),
                media);
    }

    private static SqlQuery query(CatalogProductListCriteria criteria) {
        var relation = new StringBuilder(PRODUCT_RELATION);
        var parameters = new MapSqlParameterSource();
        var cte = "";
        if (criteria.category() != null) {
            cte = CATEGORY_SCOPE;
            parameters.addValue("category", criteria.category());
            relation.append("""
                    and exists (
                        select 1
                        from catalog_product_categories assignment
                        where assignment.product_id = product.id
                          and assignment.category_id in (select id from requested_categories)
                    )
                    """);
        }
        if (criteria.collection() != null) {
            parameters.addValue("collection", criteria.collection());
            relation.append("""
                    and exists (
                        select 1
                        from catalog_collection_products membership
                        join catalog_collections collection on collection.id = membership.collection_id
                        where membership.product_id = product.id
                          and collection.slug = :collection
                          and collection.status = 'ACTIVE'
                    )
                    """);
        }
        if (criteria.onlyNew()) {
            relation.append("and product.new_arrival and (product.new_until is null or product.new_until > CURRENT_TIMESTAMP)\n");
        }
        if (criteria.search() != null) {
            parameters.addValue("search", criteria.search());
            relation.append("""
                    and (
                        product.search_document @@ websearch_to_tsquery('russian', :search)
                        or product.name OPERATOR(public.%) :search
                    )
                    """);
        }
        appendVariantFilters(relation, parameters, criteria);
        return new SqlQuery(cte, relation.toString(), orderBy(criteria), parameters);
    }

    private static void appendVariantFilters(
            StringBuilder relation, MapSqlParameterSource parameters, CatalogProductListCriteria criteria) {
        if (criteria.sizeValues().isEmpty() && criteria.colorValues().isEmpty()) {
            return;
        }
        relation.append("""
                and exists (
                    select 1
                    from catalog_product_variants filtered_variant
                    where filtered_variant.product_id = product.id
                      and filtered_variant.status = 'ACTIVE'
                """);
        if (!criteria.sizeValues().isEmpty()) {
            parameters.addValue("sizeValues", criteria.sizeValues());
            relation.append(attributeFilter("SIZE", "sizeValues"));
        }
        if (!criteria.colorValues().isEmpty()) {
            parameters.addValue("colorValues", criteria.colorValues());
            relation.append(attributeFilter("COLOR", "colorValues"));
        }
        relation.append(")\n");
    }

    private static String attributeFilter(String type, String parameter) {
        return """
                  and exists (
                      select 1
                      from catalog_variant_attribute_values filtered_value
                      join catalog_attribute_definitions filtered_definition
                        on filtered_definition.id = filtered_value.attribute_definition_id
                      where filtered_value.variant_id = filtered_variant.id
                        and filtered_definition.attribute_type = '%s'
                        and filtered_value.attribute_value in (:%s)
                  )
                """.formatted(type, parameter);
    }

    private static String orderBy(CatalogProductListCriteria criteria) {
        if (criteria.sort() == CatalogProductSort.NAME_ASC) {
            return " order by lower(product.name), product.id";
        }
        if (criteria.sort() == CatalogProductSort.NEWEST) {
            return " order by product.published_at desc, product.id";
        }
        if (criteria.collection() != null) {
            return """
                     order by (
                         select membership.display_order
                         from catalog_collection_products membership
                         join catalog_collections collection on collection.id = membership.collection_id
                         where membership.product_id = product.id and collection.slug = :collection
                     ), product.id
                    """;
        }
        if (criteria.search() != null) {
            return """
                     order by ts_rank_cd(
                                  product.search_document,
                                  websearch_to_tsquery('russian', :search)
                              ) desc,
                              public.similarity(product.name, :search) desc,
                              product.id
                    """;
        }
        return " order by product.published_at desc, product.id";
    }

    private record SqlQuery(String cte, String relation, String orderBy, MapSqlParameterSource parameters) {}

    private record ProductBaseRow(
            UUID id, String slug, String name, String shortDescription, Long priceMinor, Instant publishedAt, MediaRecord media) {}

    private static final class OptionAccumulator {

        private final String code;
        private final String name;
        private final AttributeType type;
        private final Map<String, AttributeValueRecord> values = new LinkedHashMap<>();

        private OptionAccumulator(String code, String name, AttributeType type) {
            this.code = code;
            this.name = name;
            this.type = type;
        }

        private void add(String value) {
            values.putIfAbsent(value, new AttributeValueRecord(code, name, type, value, value));
        }

        private VariantOptionRecord toRecord() {
            return new VariantOptionRecord(code, name, type, new ArrayList<>(values.values()));
        }
    }
}
