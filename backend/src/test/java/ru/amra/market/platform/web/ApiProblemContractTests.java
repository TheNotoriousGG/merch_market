package ru.amra.market.platform.web;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiProblemContractTests extends PostgreSqlIntegrationTest {

    private static final String TRACE_ID = "problem-contract-0001";
    private static final String CSRF_HEADER = "test-csrf-token-123456";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresIfMatchAndReportsStaleVersionsWithStableCodes() throws Exception {
        var categoryId = createCategory();

        unsafe(patch("/api/v1/admin/catalog/categories/{id}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Без версии\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(TraceContext.HEADER_NAME, TRACE_ID))
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        unsafe(patch("/api/v1/admin/catalog/categories/{id}", categoryId)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новая версия\"}"))
                .andExpect(status().isOk());

        unsafe(patch("/api/v1/admin/catalog/categories/{id}", categoryId)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Устаревшая версия\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"))
                .andExpect(jsonPath("$.status").value(412));
    }

    @Test
    void representsValidationAndMissingCatalogResourcesConsistently() throws Exception {
        unsafe(post("/api/v1/admin/catalog/categories")
                        .header("Idempotency-Key", "invalid-category-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isNotEmpty());

        mockMvc.perform(get("/api/v1/admin/catalog/products/{id}", UUID.randomUUID())
                        .with(catalogManager())
                        .header(TraceContext.HEADER_NAME, TRACE_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void representsAuthenticationAuthorizationAndCsrfFailuresConsistently() throws Exception {
        var unknown = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/admin/catalog/categories/{id}", unknown)
                        .header(TraceContext.HEADER_NAME, TRACE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));

        mockMvc.perform(get("/api/v1/admin/catalog/categories/{id}", unknown)
                        .with(customer())
                        .header(TraceContext.HEADER_NAME, TRACE_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .with(catalogManager())
                        .header(TraceContext.HEADER_NAME, TRACE_ID)
                        .header("X-AMRA-CSRF", CSRF_HEADER)
                        .header("Idempotency-Key", "csrf-category-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"csrf\",\"name\":\"CSRF\",\"displayOrder\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private UUID createCategory() throws Exception {
        var result = unsafe(post("/api/v1/admin/catalog/categories")
                        .header("Idempotency-Key", "problem-category-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"problem\",\"name\":\"Ошибки\",\"displayOrder\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        var location = requireNonNull(result.getResponse().getHeader("Location"));
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private org.springframework.test.web.servlet.ResultActions unsafe(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(catalogManager())
                .with(csrf())
                .header("X-AMRA-CSRF", CSRF_HEADER)
                .header(TraceContext.HEADER_NAME, TRACE_ID));
    }

    private static RequestPostProcessor catalogManager() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CATALOG_MANAGER"))
                .idToken(token -> token.subject("problem-contract-manager")
                        .claim("email_verified", true)
                        .claim("acr", "2"));
    }

    private static RequestPostProcessor customer() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                .idToken(token -> token.subject("problem-contract-customer")
                        .claim("email_verified", true)
                        .claim("acr", "2"));
    }
}
