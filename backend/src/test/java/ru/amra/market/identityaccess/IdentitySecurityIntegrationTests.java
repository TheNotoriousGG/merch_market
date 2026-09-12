package ru.amra.market.identityaccess;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest(properties = "amra.api-docs.enabled=true")
@AutoConfigureMockMvc
class IdentitySecurityIntegrationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesAnonymousSessionAndCsrfCookieWithoutExposingTokens() throws Exception {
        mockMvc.perform(get("/api/v1/session"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("AMRA_CSRF"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void exposesOnlyAllowlistedRolesFromVerifiedOidcIdentity() throws Exception {
        var login = oidcLogin()
                .idToken(token -> token.subject("customer-42")
                        .claim("name", "Амра Клиент")
                        .claim("email_verified", true)
                        .claim(
                                "realm_access",
                                Map.of("roles", List.of("CUSTOMER", "ADMINISTRATOR", "offline_access"))));

        mockMvc.perform(get("/api/v1/session").with(login))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.subject").value("customer-42"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("CUSTOMER")));
    }

    @Test
    void rejectsAdminRoleWithoutMfaClaim() throws Exception {
        var adminWithoutMfa = oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .idToken(token ->
                        token.claim("email_verified", true).claim("realm_access", Map.of("roles", List.of("ADMIN"))));

        mockMvc.perform(get("/internal/api-docs/openapi.yaml").with(adminWithoutMfa))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsForgedRoleClaimThatWasNotMappedToAnAuthority() throws Exception {
        var forgedClaim = oidcLogin()
                .idToken(token -> token.claim("acr", "2")
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))));

        mockMvc.perform(get("/internal/api-docs/openapi.yaml").with(forgedClaim))
                .andExpect(status().isForbidden());
    }

    @Test
    void permitsAdminAuthorityOnlyWithAcceptedMfaClaim() throws Exception {
        var adminWithMfa = oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .idToken(token -> token.claim("acr", "2")
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", List.of("ADMIN"))));

        mockMvc.perform(get("/internal/api-docs/openapi.yaml").with(adminWithMfa))
                .andExpect(status().isOk());
    }

    @Test
    void requiresCsrfForSessionLogout() throws Exception {
        var customer = oidcLogin().idToken(token -> token.claim("email_verified", true));

        mockMvc.perform(post("/api/v1/session/logout").with(customer)).andExpect(status().isForbidden());
        var csrfCookie =
                Objects.requireNonNull(mockMvc.perform(get("/api/v1/session").with(customer))
                        .andReturn()
                        .getResponse()
                        .getCookie("AMRA_CSRF"));

        mockMvc.perform(post("/api/v1/session/logout")
                        .with(customer)
                        .cookie(csrfCookie)
                        .header("X-AMRA-CSRF", csrfCookie.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsProtectedApiForAnUnverifiedIdentity() throws Exception {
        var unverified = oidcLogin().idToken(token -> token.claim("email_verified", false));
        var verified = oidcLogin().idToken(token -> token.claim("email_verified", true));

        mockMvc.perform(get("/api/v1/not-yet-implemented").with(unverified)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/not-yet-implemented").with(verified)).andExpect(status().isNotFound());
    }

    @Test
    void allowsCredentialedCorsOnlyFromTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/session")
                        .header("Origin", "http://localhost:3001")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3001"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/v1/session")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
