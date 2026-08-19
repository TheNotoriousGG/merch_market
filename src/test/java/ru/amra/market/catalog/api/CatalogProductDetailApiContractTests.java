package ru.amra.market.catalog.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.testing.CatalogTestData;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogProductDetailApiContractTests extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void returnsCanonicalDetailAndRedirectsHistoricalAliasDirectly() throws Exception {
        var category = data.activeCategory(null, "clothes", "Одежда", 0);
        var original = data.activeProduct(
                category,
                "old-hoodie",
                "Худи",
                Instant.parse("2026-08-19T12:00:00Z"),
                List.of(
                        data.variant("AMR-HOODIE-BLACK-M", "BLACK", "M", 0),
                        data.variant("AMR-HOODIE-BLACK-L", "BLACK", "L", 1)),
                Set.of());
        var renamed = products.save(original.changeSlug(new ProductSlug("hoodie")));
        products.save(renamed.archiveVariant(renamed.variants().get(1).id()));

        mockMvc.perform(get("/api/v1/catalog/products/hoodie"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", matchesPattern("\"[0-9a-f]{64}\"")))
                .andExpect(jsonPath("$.slug").value("hoodie"))
                .andExpect(jsonPath("$.categories[0].slug").value("clothes"))
                .andExpect(jsonPath("$.characteristics[0].definitionCode").value("material"))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].sku").value("AMR-HOODIE-BLACK-M"))
                .andExpect(jsonPath("$..objectKey").doesNotExist())
                .andExpect(jsonPath("$..status").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist());

        mockMvc.perform(get("/api/v1/catalog/products/old-hoodie"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/api/v1/catalog/products/hoodie"));
    }

    @Test
    void makesDraftArchivedAndUnknownProductsIndistinguishable() throws Exception {
        var category = data.activeCategory(null, "clothes", "Одежда", 0);
        data.draftProduct(category, "draft", "Черновик");
        var archived = data.activeProduct(
                category,
                "archived",
                "Архив",
                Instant.parse("2026-08-19T12:00:00Z"),
                List.of(data.variant("AMR-ARCHIVED-M", "BLACK", "M", 0)),
                Set.of());
        products.save(archived.archive());

        mockMvc.perform(get("/api/v1/catalog/products/draft")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/catalog/products/archived")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/catalog/products/unknown")).andExpect(status().isNotFound());
    }
}
