package ru.amra.market.platform.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiDocumentationDisabledTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void doesNotExposeContractByDefault() throws Exception {
        mockMvc.perform(get("/internal/api-docs/openapi.yaml")).andExpect(status().isNotFound());
    }
}
