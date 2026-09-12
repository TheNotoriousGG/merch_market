package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.catalog.application.CatalogProductListCriteria;
import ru.amra.market.catalog.application.CatalogProductSort;
import ru.amra.market.catalog.application.port.CatalogProductListReader;
import ru.amra.market.testing.CatalogPerformanceFixture;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
class CatalogQueryPlanAcceptanceTest extends PostgreSqlIntegrationTest {

    private static final Instant NEW_AFTER = Instant.parse("2025-01-01T00:00:00Z");

    @Autowired
    private CatalogProductListReader reader;

    @Autowired
    private JdbcTemplate runtimeJdbc;

    @Autowired
    private Flyway flyway;

    private CatalogPerformanceFixture fixture;
    private CatalogPerformanceFixture.Profile profile;

    @BeforeEach
    void loadRepresentativeDistribution() {
        fixture = new CatalogPerformanceFixture(
                new JdbcTemplate(flyway.getConfiguration().getDataSource()));
        fixture.clear();
        profile = fixture.load();
    }

    @AfterEach
    void removeRepresentativeDistribution() {
        fixture.clear();
    }

    @Test
    void keepsCriticalCatalogReadsIndexedAndBoundedAtRepresentativeVolume() {
        assertThat(runtimeJdbc.queryForObject(
                        "select count(*) from catalog_products where canonical_slug like 'perf-product-%'",
                        Integer.class))
                .isEqualTo(CatalogPerformanceFixture.PRODUCT_COUNT);
        assertThat(runtimeJdbc.queryForObject(
                        "select count(*) from catalog_product_variants where sku like 'PERF-%'", Integer.class))
                .isEqualTo(CatalogPerformanceFixture.VARIANT_COUNT);
        assertThat(runtimeJdbc.queryForObject("""
                        with recursive tree as (
                            select id, 1 as depth from catalog_categories
                            where slug = 'perf-root'
                            union all
                            select child.id, parent.depth + 1
                            from catalog_categories child join tree parent on child.parent_id = parent.id
                        )
                        select max(depth) from tree
                        """, Integer.class)).isEqualTo(5);
        assertThat(runtimeJdbc.queryForList("""
                        select category.slug, count(*) as products
                        from catalog_product_categories assignment
                        join catalog_categories category on category.id = assignment.category_id
                        where category.slug in ('perf-popular', 'perf-medium', 'perf-rare')
                        group by category.slug order by products desc
                        """))
                .extracting(row -> ((Number) requireNonNull(row.get("products"))).intValue())
                .containsExactly(7000, 2000, 1000);
        assertThat(runtimeJdbc.queryForObject("select current_setting('statement_timeout')", String.class))
                .isEqualTo("2s");

        var newest = reader.find(criteria(null, null, null, CatalogProductSort.NEWEST), NEW_AFTER);
        var searched =
                reader.find(criteria(null, null, "Limited Edition Capsule 100", CatalogProductSort.MANUAL), NEW_AFTER);
        var rare = reader.find(criteria(profile.rareCategory(), null, null, CatalogProductSort.NEWEST), NEW_AFTER);
        var collection = reader.find(criteria(null, profile.collection(), null, CatalogProductSort.MANUAL), NEW_AFTER);

        assertThat(newest.totalElements()).isEqualTo(10_000);
        assertThat(newest.products()).hasSize(24);
        assertThat(newest.products())
                .allSatisfy(product -> assertThat(product.variantOptions()).hasSize(2));
        assertThat(searched.totalElements()).isBetween(1L, 100L);
        assertThat(rare.totalElements()).isEqualTo(1000);
        assertThat(collection.totalElements()).isEqualTo(2000);
        assertThat(collection.products().getFirst().slug()).isEqualTo("perf-product-1");

        assertThat(plan("""
                        select product.id
                        from catalog_products product
                        join catalog_product_media media
                          on media.product_id = product.id and media.is_primary
                        where product.status = 'ACTIVE'
                        order by product.published_at desc, product.id
                        limit 24
                        """)).contains("ix_catalog_products__active_newest", "uq_catalog_product_media__one_primary");
        assertThat(plan("""
                        select product.id
                        from catalog_products product
                        join catalog_product_media media
                          on media.product_id = product.id and media.is_primary
                        where product.status = 'ACTIVE'
                          and (
                            product.search_document @@ websearch_to_tsquery(
                                'russian', 'Limited Edition Capsule 100')
                            or product.name OPERATOR(public.%) 'Limited Edition Capsule 100'
                          )
                        order by ts_rank_cd(
                                     product.search_document,
                                     websearch_to_tsquery('russian', 'Limited Edition Capsule 100')) desc,
                                 public.similarity(product.name, 'Limited Edition Capsule 100') desc,
                                 product.id
                        limit 24
                        """)).satisfies(plan -> {
            assertThat(plan).contains("Sort Method: top-N heapsort", "Execution Time:");
            assertThat(executionTimeMillis(plan)).isLessThan(500.0);
        });
        assertThat(plan("""
                        with recursive requested_categories as (
                            select id from catalog_categories
                            where slug = 'perf-rare' and status = 'ACTIVE'
                            union all
                            select child.id from catalog_categories child
                            join requested_categories parent on child.parent_id = parent.id
                            where child.status = 'ACTIVE'
                        )
                        select count(*)
                        from catalog_products product
                        join catalog_product_media media
                          on media.product_id = product.id and media.is_primary
                        where product.status = 'ACTIVE'
                          and exists (
                            select 1 from catalog_product_categories assignment
                            where assignment.product_id = product.id
                              and assignment.category_id in (select id from requested_categories)
                          )
                        """)).satisfies(plan -> {
            assertThat(plan).contains("Hash Semi Join", "Execution Time:");
            assertThat(executionTimeMillis(plan)).isLessThan(250.0);
        });
        assertThat(plan("""
                        select id from catalog_products
                        where lower(canonical_slug) = lower('perf-product-100')
                        """))
                .contains("uq_catalog_products__canonical_slug_ci")
                .doesNotContain("Seq Scan on catalog_products");
    }

    private String plan(String sql) {
        return String.join(
                "\n",
                runtimeJdbc.query(
                        "explain (analyze, buffers, costs, summary) " + sql,
                        (result, rowNumber) -> result.getString(1)));
    }

    private static double executionTimeMillis(String plan) {
        var marker = "Execution Time: ";
        var start = plan.lastIndexOf(marker);
        var end = plan.indexOf(" ms", start);
        return Double.parseDouble(plan.substring(start + marker.length(), end));
    }

    private static CatalogProductListCriteria criteria(
            @org.jspecify.annotations.Nullable String category,
            @org.jspecify.annotations.Nullable String collection,
            @org.jspecify.annotations.Nullable String search,
            CatalogProductSort sort) {
        return new CatalogProductListCriteria(0, 24, category, collection, search, false, List.of(), List.of(), sort);
    }
}
