package ru.amra.market.catalog.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import ru.amra.market.testing.CatalogTestData;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogProductListApiContractTests extends PostgreSqlIntegrationTest {

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
    void returnsContractShapedCardsToAnonymousGuestsWithoutPrivateFields() throws Exception {
        var category = data.activeCategory(null, "clothes", "Одежда", 0);
        var product = data.activeProduct(
                category,
                "hoodie-mono",
                "Худи Mono",
                Instant.parse("2026-08-10T10:00:00Z"),
                List.of(data.variant("AMR-HOODIE-BLACK-M", "BLACK", "M", 0)),
                Set.of());

        mockMvc.perform(get("/api/v1/catalog/products").queryParam("sort", "NEWEST"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("ETag", matchesPattern("\"[0-9a-f]{64}\"")))
                .andExpect(header().string("Cache-Control", containsString("max-age=30")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(product.id().value().toString()))
                .andExpect(jsonPath("$.items[0].slug").value("hoodie-mono"))
                .andExpect(jsonPath("$.items[0].primaryMedia.url")
                        .value(matchesPattern(
                                "https://cdn\\.amra-shop\\.invalid/media/catalog/[0-9a-f-]+/primary\\.webp")))
                .andExpect(jsonPath("$.items[0].variantOptions.length()").value(2))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(24))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$..objectKey").doesNotExist())
                .andExpect(jsonPath("$..price").doesNotExist())
                .andExpect(jsonPath("$..stock").doesNotExist());
    }

    @Test
    void rejectsUnsupportedSortAndWhitespaceOnlySearch() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products").queryParam("sort", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/catalog/products").queryParam("q", "  ")).andExpect(status().isBadRequest());
    }
}
