package ru.amra.market.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class providing isolated PostgreSQL connection properties to Spring integration tests. */
public abstract class PostgreSqlIntegrationTest {

    @DynamicPropertySource
    protected static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.registerProperties(registry);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-secret", () -> "test-only-secret");
        registry.add("server.servlet.session.cookie.secure", () -> "false");
        registry.add("amra.inventory.expiry.enabled", () -> "false");
    }
}
