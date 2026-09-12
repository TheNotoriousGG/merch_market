package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.amra.market.catalog.application.port.CatalogIdGenerator;

/** PostgreSQL-native UUIDv7 generator. */
@Component
class JdbcCatalogIdGenerator implements CatalogIdGenerator {

    private final JdbcTemplate jdbc;

    JdbcCatalogIdGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID next() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
