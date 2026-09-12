package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.StorefrontBannerRepository;
import ru.amra.market.catalog.domain.StorefrontBanner;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StorefrontBannerApiTests extends PostgreSqlIntegrationTest {

    private static final String CSRF_VALUE = "banner-csrf-token-123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StorefrontBannerRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void exposesOnlyPublishedScheduledBannersToAnonymousStorefront() throws Exception {
        var id = nextId();
        repository.save(new StorefrontBanner(
                id,
                "Главная",
                "Новая коллекция",
                "Худи AMRA",
                "Описание коллекции",
                "Смотреть",
                StorefrontBanner.TargetType.SUBCATEGORY,
                "clothes/hoodies",
                "banners/" + id + "/uploads/hero.webp",
                null,
                StorefrontBanner.Status.PUBLISHED,
                0,
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2030-01-01T00:00:00Z"),
                0,
                Instant.parse("2026-09-12T12:00:00Z")));

        mockMvc.perform(get("/api/v1/storefront/banners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(id.toString())))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].targetUrl".formatted(id))
                        .value(hasItem("/catalog/clothes?section=hoodies")))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].desktopImageUrl".formatted(id))
                        .value(hasItem(matchesPattern(".*hero\\.webp"))));
    }

    @Test
    void createsReadsAndUpdatesDraftWithStrongConcurrency() throws Exception {
        var created = mockMvc.perform(authorized(post("/api/v1/admin/storefront/banners"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Первый заголовок")))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"v0\""))
                .andExpect(header().string("Location", matchesPattern("/api/v1/admin/storefront/banners/[0-9a-f-]+")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        var location = requireNonNull(created.getResponse().getHeader("Location"));
        var id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(get("/api/v1/admin/storefront/banners/{id}", id).with(catalogManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v0\""));

        mockMvc.perform(authorized(
                                put("/api/v1/admin/storefront/banners/{id}", id).header("If-Match", "\"v0\""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Обновлённый заголовок")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.title").value("Обновлённый заголовок"));

        mockMvc.perform(authorized(
                                put("/api/v1/admin/storefront/banners/{id}", id).header("If-Match", "\"v0\""))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Потерянное изменение")))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void protectsAdministrationWithSessionRoleMfaAndCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/admin/storefront/banners")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/storefront/banners")
                        .with(oidcLogin().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Баннер")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/storefront/banners")
                        .with(catalogManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("Баннер")))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.with(catalogManager()).with(csrf()).header("X-AMRA-CSRF", CSRF_VALUE);
    }

    private static RequestPostProcessor catalogManager() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"))
                .idToken(token -> token.subject("banner-manager")
                        .claim("email_verified", true)
                        .claim("acr", "2"));
    }

    private static String payload(String title) {
        return """
                {
                  "internalName":"Главный баннер",
                  "eyebrow":"Новая коллекция",
                  "title":"%s",
                  "description":"Описание коллекции",
                  "buttonLabel":"Смотреть",
                  "targetType":"CATEGORY",
                  "targetValue":"clothes",
                  "status":"DRAFT",
                  "displayOrder":0
                }
                """.formatted(title);
    }

    private UUID nextId() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
