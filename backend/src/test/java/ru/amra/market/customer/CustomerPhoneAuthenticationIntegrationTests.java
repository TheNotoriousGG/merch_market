package ru.amra.market.customer;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "amra.customer.phone-auth.expose-development-code=true")
@Transactional
class CustomerPhoneAuthenticationIntegrationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void signsInByNormalizedPhonePersistsSessionAndLogsOut() throws Exception {
        var started = mockMvc.perform(post("/api/v1/customer/auth/phone/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"8 (999) 123-45-67\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+79991234567"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.developmentCode").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var challengeId = UUID.fromString(JsonPath.read(started, "$.challengeId"));
        var code = (String) JsonPath.read(started, "$.developmentCode");

        mockMvc.perform(post("/api/v1/customer/auth/phone/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+79991234567\"}"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/v1/customer/auth/phone/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"%s\",\"code\":\"000000\"}".formatted(challengeId)))
                .andExpect(status().isBadRequest());

        var verified = mockMvc.perform(post("/api/v1/customer/auth/phone/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challengeId, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+79991234567"))
                .andReturn();
        var sessionCookie = requireNonNull(verified.getResponse().getCookie("SESSION"));

        mockMvc.perform(get("/api/v1/customer/account").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+79991234567"));

        mockMvc.perform(post("/api/v1/customer/auth/logout").with(csrf()).cookie(sessionCookie))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/customer/account").cookie(sessionCookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedPhoneAndUnknownChallenge() throws Exception {
        mockMvc.perform(post("/api/v1/customer/auth/phone/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/customer/auth/phone/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"%s\",\"code\":\"123456\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
