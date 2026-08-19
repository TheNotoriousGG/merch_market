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
import ru.amra.market.catalog.application.port.CatalogProductReferenceReader;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.testing.CatalogTestData;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@Transactional
class CatalogProductReferenceReaderIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private CatalogProductReferenceReader reader;

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
    void returnsOnlyVisibleReferencesInPresentationOrder() {
        var category = data.activeCategory(null, "clothes", "Одежда", 0);
        var collection = data.activeCollection("base", "Base");
        var product = data.activeProduct(
                category,
                "hoodie",
                "Худи",
                Instant.parse("2026-08-19T12:00:00Z"),
                List.of(data.variant("AMR-HOODIE-M", "BLACK", "M", 0)),
                Set.of(collection));

        var visible = reader.findFor(product.id());
        jdbc.update(
                "update catalog_categories set status = 'HIDDEN' where id = ?",
                category.id().value());
        jdbc.update("update catalog_collections set status = 'HIDDEN' where id = ?", collection.value());
        var hidden = reader.findFor(product.id());

        assertThat(visible.categories())
                .extracting(CatalogProductReferenceReader.CategoryRecord::slug)
                .containsExactly("clothes");
        assertThat(visible.collections())
                .extracting(CatalogProductReferenceReader.CollectionRecord::slug)
                .containsExactly("base");
        assertThat(hidden.categories()).isEmpty();
        assertThat(hidden.collections()).isEmpty();
    }
}
