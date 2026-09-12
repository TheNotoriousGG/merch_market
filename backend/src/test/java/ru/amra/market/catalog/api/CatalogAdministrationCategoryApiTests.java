package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogAdministrationCategoryApiTests extends PostgreSqlIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "category-create-0001";
    private static final String CSRF_HEADER_VALUE = "test-csrf-token-123456";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsReadsUpdatesAndReplaysCategoryWithStrongConcurrency() throws Exception {
        var first = create("clothes", "Одежда", IDEMPOTENCY_KEY)
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location", matchesPattern("/api/v1/admin/catalog/categories/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        var id = idFromLocation(requireNonNull(first.getResponse().getHeader("Location")));

        create("clothes", "Одежда", IDEMPOTENCY_KEY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(get("/api/v1/admin/catalog/categories/{id}", id).with(catalogManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v0\""));

        mockMvc.perform(patch("/api/v1/admin/catalog/categories/{id}", id)
                        .with(catalogManager())
                        .with(validCsrf())
                        .header("X-AMRA-CSRF", CSRF_HEADER_VALUE)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"name\":\"Одежда и обувь\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.name").value("Одежда и обувь"));

        mockMvc.perform(patch("/api/v1/admin/catalog/categories/{id}", id)
                        .with(catalogManager())
                        .with(validCsrf())
                        .header("X-AMRA-CSRF", CSRF_HEADER_VALUE)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Потерянное изменение\"}"))
                .andExpect(status().isPreconditionFailed());

        create("accessories", "Аксессуары", IDEMPOTENCY_KEY).andExpect(status().isConflict());
    }

    @Test
    void validatesMoveCycleAndExplicitRootTransition() throws Exception {
        var rootId = createdId(create("root", "Корень", "category-create-root"));
        var childId = createdId(createChild(rootId, "child", "Потомок", "category-create-child"));

        mockMvc.perform(patch("/api/v1/admin/catalog/categories/{id}", rootId)
                        .with(catalogManager())
                        .with(validCsrf())
                        .header("X-AMRA-CSRF", CSRF_HEADER_VALUE)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + childId + "\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/admin/catalog/categories/{id}", childId)
                        .with(catalogManager())
                        .with(validCsrf())
                        .header("X-AMRA-CSRF", CSRF_HEADER_VALUE)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearParent\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.parentId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void enforcesCatalogRoleVerifiedIdentityMfaAndCsrf() throws Exception {
        mockMvc.perform(createRequest().with(validCsrf()).header("X-AMRA-CSRF", CSRF_HEADER_VALUE))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(createRequest()
                        .with(customerWithMfa())
                        .with(validCsrf())
                        .header("X-AMRA-CSRF", CSRF_HEADER_VALUE))
                .andExpect(status().isForbidden());
        mockMvc.perform(createRequest()
                        .with(catalogManagerWithoutMfa())
                        .with(validCsrf())
                        .header("X-AMRA-CSRF", CSRF_HEADER_VALUE))
                .andExpect(status().isForbidden());
        mockMvc.perform(createRequest()
                        .with(unverifiedCatalogManager())
                        .with(validCsrf())
                        .header("X-AMRA-CSRF", CSRF_HEADER_VALUE))
                .andExpect(status().isForbidden());
        mockMvc.perform(createRequest().with(catalogManager()).header("X-AMRA-CSRF", CSRF_HEADER_VALUE))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions create(String slug, String name, String key)
            throws Exception {
        return mockMvc.perform(post("/api/v1/admin/catalog/categories")
                .with(catalogManager())
                .with(validCsrf())
                .header("X-AMRA-CSRF", CSRF_HEADER_VALUE)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + slug + "\",\"name\":\"" + name + "\",\"displayOrder\":0}"));
    }

    private org.springframework.test.web.servlet.ResultActions createChild(
            UUID parentId, String slug, String name, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/catalog/categories")
                .with(catalogManager())
                .with(validCsrf())
                .header("X-AMRA-CSRF", CSRF_HEADER_VALUE)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + parentId + "\",\"slug\":\"" + slug + "\",\"name\":\"" + name
                        + "\",\"displayOrder\":0}"));
    }

    private static UUID createdId(org.springframework.test.web.servlet.ResultActions action) throws Exception {
        var result = action.andExpect(status().isCreated()).andReturn();
        return idFromLocation(requireNonNull(result.getResponse().getHeader("Location")));
    }

    private static UUID idFromLocation(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private static MockHttpServletRequestBuilder createRequest() {
        return post("/api/v1/admin/catalog/categories")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"clothes\",\"name\":\"Одежда\",\"displayOrder\":0}");
    }

    private static RequestPostProcessor validCsrf() {
        return csrf();
    }

    private static RequestPostProcessor catalogManager() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"))
                .idToken(token -> token.subject("catalog-manager-1")
                        .claim("email_verified", true)
                        .claim("acr", "2"));
    }

    private static RequestPostProcessor customerWithMfa() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                .idToken(token -> token.claim("email_verified", true).claim("acr", "2"));
    }

    private static RequestPostProcessor catalogManagerWithoutMfa() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"))
                .idToken(token -> token.claim("email_verified", true));
    }

    private static RequestPostProcessor unverifiedCatalogManager() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"))
                .idToken(token -> token.claim("email_verified", false).claim("acr", "2"));
    }
}
