package ru.amra.market.platform.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest(properties = "amra.api-docs.enabled=true")
@AutoConfigureMockMvc
class PlatformApiContractTests extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApiMetadataDefinedByTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.name").value("amra-merch-market-backend"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.serverTime").value(matchesPattern(".*Z$")));
    }

    @Test
    void servesCanonicalContractWhenDocumentationIsExplicitlyEnabled() throws Exception {
        mockMvc.perform(get("/internal/api-docs/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/yaml"))
                .andExpect(content().string(matchesPattern("(?s).*openapi: 3\\.0\\.3.*")));

        mockMvc.perform(get("/internal/api-docs/components/schemas/problem-details.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*RFC 9457 problem details.*")));

        mockMvc.perform(get("/internal/api-docs/components/schemas/missing.yaml"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/internal/api-docs/components/schemas/INVALID.yaml"))
                .andExpect(status().isNotFound());
    }
}
