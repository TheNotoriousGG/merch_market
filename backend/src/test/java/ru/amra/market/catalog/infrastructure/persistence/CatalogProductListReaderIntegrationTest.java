package ru.amra.market.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.CatalogProductListCriteria;
import ru.amra.market.catalog.application.CatalogProductSort;
import ru.amra.market.catalog.application.port.CatalogProductListReader;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.testing.CatalogTestData;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@Transactional
class CatalogProductListReaderIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant CUTOFF = Instant.parse("2026-07-20T12:00:00Z");

    @Autowired
    private CatalogProductListReader reader;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductRepository products;

    @Autowired
    private JdbcTemplate jdbc;

    private CatalogTestData data;

    @BeforeEach
    void setUp() {
        data = new CatalogTestData(categories, products, jdbc);
    }

    @Test
    void paginatesOnlyActiveProductsWithExactTotalsAndStableNewestOrder() {
        var category = data.activeCategory(null, "clothes", "Одежда", 0);
        data.activeProduct(
                category, "first", "Первый", Instant.parse("2026-08-01T10:00:00Z"), variants("FIRST"), Set.of());
        data.activeProduct(
                category, "third", "Третий", Instant.parse("2026-08-03T10:00:00Z"), variants("THIRD"), Set.of());
        data.activeProduct(
                category, "second", "Второй", Instant.parse("2026-08-02T10:00:00Z"), variants("SECOND"), Set.of());
        data.draftProduct(category, "draft", "Черновик");

        var firstPage = reader.find(
                criteria(0, 2, null, null, null, false, List.of(), List.of(), CatalogProductSort.NEWEST), CUTOFF);
        var secondPage = reader.find(
                criteria(1, 2, null, null, null, false, List.of(), List.of(), CatalogProductSort.NEWEST), CUTOFF);

        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.products())
                .extracting(CatalogProductListReader.ProductRecord::slug)
                .containsExactly("third", "second");
        assertThat(secondPage.products())
                .extracting(CatalogProductListReader.ProductRecord::slug)
                .containsExactly("first");
        assertThat(firstPage.products())
                .allSatisfy(product -> assertThat(product.variantOptions()).hasSize(2));
    }

    @Test
    void categoryFilterIncludesVisibleDescendants() {
        var root = data.activeCategory(null, "clothes", "Одежда", 0);
        var child = data.activeCategory(root.id(), "hoodies", "Худи", 0);
        data.activeProduct(
                child, "hoodie", "Худи Mono", Instant.parse("2026-08-01T10:00:00Z"), variants("HOODIE"), Set.of());

        var result = reader.find(
                criteria(0, 24, "clothes", null, null, false, List.of(), List.of(), CatalogProductSort.MANUAL), CUTOFF);

        assertThat(result.products())
                .extracting(CatalogProductListReader.ProductRecord::slug)
                .containsExactly("hoodie");
    }

    @Test
    void sizeAndColorMustMatchTheSameActiveVariant() {
        var category = data.activeCategory(null, "clothes", "Одежда", 0);
        data.activeProduct(
                category,
                "split-options",
                "Раздельные варианты",
                Instant.parse("2026-08-01T10:00:00Z"),
                List.of(
                        data.variant("AMR-SPLIT-RED-S", "RED", "S", 0),
                        data.variant("AMR-SPLIT-BLUE-M", "BLUE", "M", 1)),
                Set.of());
        data.activeProduct(
                category,
                "exact-options",
                "Точное сочетание",
                Instant.parse("2026-08-02T10:00:00Z"),
                List.of(data.variant("AMR-EXACT-RED-M", "RED", "M", 0)),
                Set.of());

        var result = reader.find(
                criteria(0, 24, null, null, null, false, List.of("M"), List.of("RED"), CatalogProductSort.MANUAL),
                CUTOFF);

        assertThat(result.products())
                .extracting(CatalogProductListReader.ProductRecord::slug)
                .containsExactly("exact-options");
    }

    @Test
    void combinesCollectionNewWindowAndTypoTolerantSearch() {
        var category = data.activeCategory(null, "clothes", "Одежда", 0);
        var collection = data.activeCollection("base", "Base");
        data.activeProduct(
                category,
                "hoodie-mono",
                "Hoodie Mono",
                Instant.parse("2026-08-10T10:00:00Z"),
                variants("MONO"),
                Set.of(collection));
        data.activeProduct(
                category,
                "old-hoodie",
                "Old Hoodie",
                Instant.parse("2026-06-01T10:00:00Z"),
                variants("OLD"),
                Set.of(collection));

        var result = reader.find(
                criteria(0, 24, null, "base", "Hodie Mono", true, List.of(), List.of(), CatalogProductSort.MANUAL),
                CUTOFF);

        assertThat(result.products())
                .extracting(CatalogProductListReader.ProductRecord::slug)
                .containsExactly("hoodie-mono");
    }

    private List<ru.amra.market.catalog.domain.ProductVariant> variants(String suffix) {
        return List.of(data.variant("AMR-" + suffix + "-BLACK-M", "BLACK", "M", 0));
    }

    private static CatalogProductListCriteria criteria(
            int page,
            int size,
            @org.jspecify.annotations.Nullable String category,
            @org.jspecify.annotations.Nullable String collection,
            @org.jspecify.annotations.Nullable String search,
            boolean onlyNew,
            List<String> sizes,
            List<String> colors,
            CatalogProductSort sort) {
        return new CatalogProductListCriteria(page, size, category, collection, search, onlyNew, sizes, colors, sort);
    }
}
