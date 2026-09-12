package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.amra.market.inventory.application.port.InventoryIdGenerator;

/** PostgreSQL-native UUIDv7 generator for inventory-owned identities. */
@Component
final class JdbcInventoryIdGenerator implements InventoryIdGenerator {

    private final JdbcTemplate jdbc;

    JdbcInventoryIdGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID next() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
