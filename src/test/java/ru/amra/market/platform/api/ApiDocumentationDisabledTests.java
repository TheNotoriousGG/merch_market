package ru.amra.market.platform.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class ApiDocumentationDisabledTests extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void doesNotExposeContractByDefault() throws Exception {
        var adminWithMfa = oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .idToken(token -> token.claim("acr", "2"));

        mockMvc.perform(get("/internal/api-docs/openapi.yaml").with(adminWithMfa))
                .andExpect(status().isNotFound());
    }
}
