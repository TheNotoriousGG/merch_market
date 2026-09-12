package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryName;
import ru.amra.market.catalog.domain.CategorySlug;
import ru.amra.market.catalog.domain.CategoryStatus;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogCategoryApiContractTests extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsOnlyReachableVisibleCategoriesToAnAnonymousGuest() throws Exception {
        activeCategory(null, "clothes", "Одежда", 20);
        var accessories = activeCategory(null, "accessories", "Аксессуары", 10);
        activeCategory(accessories.id(), "watches", "Часы", 20);
        activeCategory(accessories.id(), "bags", "Сумки", 10);
        var hidden = hiddenCategory(null, "hidden", "Скрытая ветка", 0);
        activeCategory(hidden.id(), "hidden-child", "Скрытый потомок", 0);

        var response = mockMvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("ETag", matchesPattern("\"[0-9a-f]{64}\"")))
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(header().string("Cache-Control", containsString("stale-while-revalidate=300")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].slug").value("accessories"))
                .andExpect(jsonPath("$.categories[0].children[0].slug").value("bags"))
                .andExpect(jsonPath("$.categories[0].children[1].slug").value("watches"))
                .andExpect(jsonPath("$.categories[1].slug").value("clothes"))
                .andExpect(jsonPath("$..status").doesNotExist())
                .andExpect(jsonPath("$..version").doesNotExist())
                .andReturn();

        var etag = requireNonNull(response.getResponse().getHeader("ETag"));
        mockMvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", etag));
    }

    @Test
    void changesEtagWhenVisibleRepresentationChanges() throws Exception {
        var clothes = activeCategory(null, "clothes", "Одежда", 0);
        var initial = requireNonNull(mockMvc.perform(get("/api/v1/catalog/categories"))
                .andReturn()
                .getResponse()
                .getHeader("ETag"));

        categories.save(
                clothes.change(null, clothes.slug(), new CategoryName("Одежда и обувь"), 0, CategoryStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", matchesPattern("\"[0-9a-f]{64}\"")))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.not(initial)))
                .andExpect(jsonPath("$.categories[0].name").value("Одежда и обувь"));
    }

    private Category activeCategory(
            @org.jspecify.annotations.Nullable CategoryId parentId, String slug, String name, int order) {
        var hidden = hiddenCategory(parentId, slug, name, order);
        return categories.save(
                hidden.change(parentId, hidden.slug(), hidden.name(), hidden.displayOrder(), CategoryStatus.ACTIVE));
    }

    private Category hiddenCategory(
            @org.jspecify.annotations.Nullable CategoryId parentId, String slug, String name, int order) {
        return categories.save(Category.create(
                new CategoryId(requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class))),
                parentId,
                new CategorySlug(slug),
                new CategoryName(name),
                order));
    }
}
