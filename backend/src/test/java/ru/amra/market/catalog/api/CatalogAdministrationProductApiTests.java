package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogAdministrationProductApiTests extends PostgreSqlIntegrationTest {

    private static final String CSRF_HEADER = "test-csrf-token-123456";
    private static final String TRACE_ID = "catalog-admin-test-0001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void administersProductVariantMediaAndLifecycleWithRootConcurrency() throws Exception {
        var categoryId = activeCategory("clothes", "Одежда", "category-product-01");
        var productId = draftProduct(categoryId, "futbolka", "product-create-001");

        unsafe(patch("/api/v1/admin/catalog/products/{id}", productId)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Футболка AMRA\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""));

        var variant = unsafe(post("/api/v1/admin/catalog/products/{id}/variants", productId)
                        .header("If-Match", "\"v1\"")
                        .header("Idempotency-Key", "variant-create-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku":"AMR-TS01-BLK-M",
                                  "label":"Графит / M",
                                  "displayOrder":0,
                                  "attributes":[{
                                    "definitionCode":"size",
                                    "definitionName":"Размер",
                                    "type":"SIZE",
                                    "valueCode":"M",
                                    "label":"M"
                                  }]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v2\""))
                .andExpect(jsonPath("$.attributes[0].label").value("M"))
                .andReturn();
        var variantId = UUID.fromString(requireNonNull(
                com.jayway.jsonpath.JsonPath.read(variant.getResponse().getContentAsString(), "$.id")));

        unsafe(patch("/api/v1/admin/catalog/products/{id}/variants/{variantId}", productId, variantId)
                        .header("If-Match", "\"v2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Чёрный / M\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.version").value(1));

        var media = unsafe(post("/api/v1/admin/catalog/products/{id}/media", productId)
                        .header("If-Match", "\"v3\"")
                        .header("Idempotency-Key", "media-create-00001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey":"catalog/products/futbolka/primary.webp",
                                  "contentType":"image/webp",
                                  "width":1200,
                                  "height":1500,
                                  "alt":"Футболка AMRA",
                                  "displayOrder":0,
                                  "primary":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v4\""))
                .andReturn();
        var mediaId = UUID.fromString(requireNonNull(
                com.jayway.jsonpath.JsonPath.read(media.getResponse().getContentAsString(), "$.id")));

        unsafe(patch("/api/v1/admin/catalog/products/{id}/media/{mediaId}", productId, mediaId)
                        .header("If-Match", "\"v4\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alt\":\"Футболка AMRA, вид спереди\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v5\""))
                .andExpect(jsonPath("$.version").value(1));

        var publish = post("/api/v1/admin/catalog/products/{id}/publish", productId)
                .header("If-Match", "\"v5\"")
                .header("Idempotency-Key", "product-publish-001");
        unsafe(publish)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v6\""))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        unsafe(post("/api/v1/admin/catalog/products/{id}/publish", productId)
                        .header("If-Match", "\"v5\"")
                        .header("Idempotency-Key", "product-publish-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v6\""));

        unsafe(patch("/api/v1/admin/catalog/products/{id}/media/{mediaId}", productId, mediaId)
                        .header("If-Match", "\"v6\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primary\":false}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/admin/catalog/products/{id}", productId).with(catalogManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v6\""))
                .andExpect(jsonPath("$.publishedAt").exists());

        var audit = jdbc.queryForList("""
                select entity_type, action, actor_scope, safe_diff::text as safe_diff
                from catalog_audit_events
                where correlation_id = ?
                order by occurred_at, id
                """, TRACE_ID);
        assertThat(audit).hasSize(9);
        assertThat(audit)
                .extracting(row -> row.get("action"))
                .containsExactly(
                        "CREATED",
                        "UPDATED",
                        "CREATED",
                        "UPDATED",
                        "CREATED",
                        "UPDATED",
                        "CREATED",
                        "UPDATED",
                        "PUBLISHED");
        assertThat(audit).allSatisfy(row -> {
            assertThat(row.get("actor_scope")).isEqualTo("catalog-manager-products");
            assertThat(requireNonNull(row.get("safe_diff")).toString()).doesNotContain("objectKey", "primary.webp");
        });
    }

    @Test
    void ownsOrderedCollectionMembershipAndInvalidatesAffectedProductVersion() throws Exception {
        var categoryId = activeCategory("accessories", "Аксессуары", "category-product-02");
        var productId = draftProduct(categoryId, "sumka", "product-create-002");

        var collection = unsafe(post("/api/v1/admin/catalog/collections")
                        .header("Idempotency-Key", "collection-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug":"week-selection",
                                  "name":"Выбор недели",
                                  "description":"Редакционная подборка",
                                  "displayOrder":0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/admin/catalog/collections/[0-9a-f-]+")))
                .andReturn();
        var collectionId =
                idFromLocation(requireNonNull(collection.getResponse().getHeader("Location")));
        mockMvc.perform(get("/api/v1/admin/catalog/collections").with(catalogManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(collectionId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Выбор недели"));

        var membershipBody = "{\"productIds\":[\"" + productId + "\",\"" + productId + "\"]}";
        unsafe(put("/api/v1/admin/catalog/collections/{id}/products", collectionId)
                        .header("If-Match", "\"v0\"")
                        .header("Idempotency-Key", "collection-members-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(membershipBody))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.productIds", hasSize(1)));

        mockMvc.perform(get("/api/v1/admin/catalog/products/{id}", productId).with(catalogManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.collectionIds[0]").value(collectionId.toString()));

        unsafe(patch("/api/v1/admin/catalog/collections/{id}", collectionId)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""));

        unsafe(patch("/api/v1/admin/catalog/products/{id}", productId)
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"collectionIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""));

        mockMvc.perform(get("/api/v1/admin/catalog/collections/{id}", collectionId)
                        .with(catalogManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.productIds", hasSize(0)));

        unsafe(put("/api/v1/admin/catalog/collections/{id}/products", collectionId)
                        .header("If-Match", "\"v0\"")
                        .header("Idempotency-Key", "collection-members-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(membershipBody))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.productIds", hasSize(0)));
    }

    private UUID activeCategory(String slug, String name, String key) throws Exception {
        var created = unsafe(post("/api/v1/admin/catalog/categories")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"" + slug + "\",\"name\":\"" + name + "\",\"displayOrder\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        var id = idFromLocation(requireNonNull(created.getResponse().getHeader("Location")));
        unsafe(patch("/api/v1/admin/catalog/categories/{id}", id)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private UUID draftProduct(UUID categoryId, String slug, String key) throws Exception {
        var created = unsafe(post("/api/v1/admin/catalog/products")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug":"%s",
                                  "name":"Товар AMRA",
                                  "shortDescription":"Короткое описание",
                                  "description":"Полное описание товара",
                                  "priceMinor":549000,
                                  "primaryCategoryId":"%s",
                                  "categoryIds":["%s"]
                                }
                                """.formatted(slug, categoryId, categoryId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andReturn();
        return idFromLocation(requireNonNull(created.getResponse().getHeader("Location")));
    }

    private ResultActions unsafe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.with(catalogManager())
                .with(csrf())
                .header("X-AMRA-CSRF", CSRF_HEADER)
                .header("X-Trace-Id", TRACE_ID));
    }

    private static UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private static RequestPostProcessor catalogManager() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"))
                .idToken(token -> token.subject("catalog-manager-products")
                        .claim("email_verified", true)
                        .claim("acr", "2"));
    }
}
