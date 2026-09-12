package ru.amra.market.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class SessionLifetimePolicyTests {

    private final SecurityProperties properties = new SecurityProperties(
            List.of("https://shop.example"),
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            Duration.ofMinutes(15),
            Duration.ofHours(8),
            List.of("2"));

    private final SessionLifetimePolicy policy = new SessionLifetimePolicy(properties);

    @Test
    void appliesShorterLifetimeToAdministrators() {
        var authentication =
                new TestingAuthenticationToken("admin", "ignored", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertThat(policy.idleTimeout(authentication)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.absoluteTimeout(authentication)).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void appliesCustomerLifetimeWithoutAdministratorPermission() {
        var authentication = new TestingAuthenticationToken(
                "customer", "ignored", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));

        assertThat(policy.idleTimeout(authentication)).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.absoluteTimeout(authentication)).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void rejectsWildcardCorsOrigins() {
        assertThatThrownBy(() -> new SecurityProperties(
                        List.of("*"),
                        Duration.ofMinutes(30),
                        Duration.ofDays(30),
                        Duration.ofMinutes(15),
                        Duration.ofHours(8),
                        List.of("2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact HTTP origins");
    }

    @Test
    void rejectsUnsafeOrInconsistentSecurityPolicy() {
        assertThatThrownBy(() -> new SecurityProperties(
                        List.of("https://shop.example/path"),
                        Duration.ofMinutes(30),
                        Duration.ofDays(30),
                        Duration.ofMinutes(15),
                        Duration.ofHours(8),
                        List.of("2")))
                .hasMessageContaining("exact HTTP origins");

        assertThatThrownBy(() -> new SecurityProperties(
                        List.of("https://shop.example"),
                        Duration.ofMinutes(30),
                        Duration.ofDays(30),
                        Duration.ofMinutes(15),
                        Duration.ofHours(8),
                        List.of(" ")))
                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> new SecurityProperties(
                        List.of("https://shop.example"),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(15),
                        Duration.ofHours(8),
                        List.of("2")))
                .hasMessageContaining("shorter than absolute");

        assertThatThrownBy(() -> new SecurityProperties(
                        List.of("https://shop.example"),
                        Duration.ZERO,
                        Duration.ofDays(30),
                        Duration.ofMinutes(15),
                        Duration.ofHours(8),
                        List.of("2")))
                .hasMessageContaining("must be positive");
    }
}
