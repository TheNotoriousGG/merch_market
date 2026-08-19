package ru.amra.market.testing;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class providing isolated PostgreSQL connection properties to Spring integration tests. */
public abstract class PostgreSqlIntegrationTest {

    @DynamicPropertySource
    protected static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.registerProperties(registry);
    }
}
