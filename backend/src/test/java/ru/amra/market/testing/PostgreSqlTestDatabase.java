package ru.amra.market.testing;

import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Shared PostgreSQL 18 database with production-equivalent role separation for integration tests. */
public final class PostgreSqlTestDatabase {

    private static final String IMAGE =
            "postgres:18.4-alpine@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15";
    private static final String DATABASE_NAME = "amra_market";
    private static final String OWNER_USER = "amra_owner";
    private static final String OWNER_PASSWORD = "owner-test-password";
    private static final String MIGRATION_USER = "amra_migrator";
    private static final String MIGRATION_PASSWORD = "migration-test-password";
    private static final String RUNTIME_USER = "amra_runtime";
    private static final String RUNTIME_PASSWORD = "runtime-test-password";

    private static final PostgreSQLContainer DATABASE = createDatabase();

    private PostgreSqlTestDatabase() {}

    public static void registerProperties(DynamicPropertyRegistry registry) {
        start();
        registry.add("spring.datasource.url", PostgreSqlTestDatabase::applicationJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 1);
        registry.add("spring.flyway.url", PostgreSqlTestDatabase::applicationJdbcUrl);
        registry.add("spring.flyway.user", () -> MIGRATION_USER);
        registry.add("spring.flyway.password", () -> MIGRATION_PASSWORD);
    }

    private static PostgreSQLContainer createDatabase() {
        var image = DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres");
        var bootstrap = MountableFile.forHostPath(Path.of("database/bootstrap/01-roles.sh"), 0755);
        return new PostgreSQLContainer(image)
                .withDatabaseName(DATABASE_NAME)
                .withUsername(OWNER_USER)
                .withPassword(OWNER_PASSWORD)
                .withEnv("AMRA_MIGRATION_PASSWORD", MIGRATION_PASSWORD)
                .withEnv("AMRA_RUNTIME_PASSWORD", RUNTIME_PASSWORD)
                .withCopyFileToContainer(bootstrap, "/docker-entrypoint-initdb.d/01-roles.sh");
    }

    private static void start() {
        if (!DATABASE.isRunning()) {
            DATABASE.start();
        }
    }

    private static String applicationJdbcUrl() {
        return DATABASE.getJdbcUrl() + "?currentSchema=amra_shop&ApplicationName=amra-merch-market-integration-tests";
    }
}
