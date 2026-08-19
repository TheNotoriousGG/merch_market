package ru.amra.market.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AbsoluteSessionLifetimeFilterTests {

    private final SecurityProperties properties = new SecurityProperties(
            List.of("https://shop.example"),
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            Duration.ofMinutes(15),
            Duration.ofHours(8),
            List.of("2"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidatesAdministratorSessionAtItsAbsoluteDeadline() throws Exception {
        var session = new MockHttpSession();
        var deadline = Instant.ofEpochMilli(session.getCreationTime()).plus(Duration.ofHours(8));
        var filter = new AbsoluteSessionLifetimeFilter(
                Clock.fixed(deadline, ZoneOffset.UTC), new SessionLifetimePolicy(properties));
        var request = new MockHttpServletRequest();
        request.setSession(session);
        var response = new MockHttpServletResponse();
        var authentication =
                new TestingAuthenticationToken("admin", "ignored", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
