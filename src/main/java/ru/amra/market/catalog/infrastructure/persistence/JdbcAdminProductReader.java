package ru.amra.market.catalog.infrastructure.persistence;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.port.AdminProductReader;
import ru.amra.market.catalog.domain.ProductId;

/** JDBC projection for product root timestamps not owned by the domain aggregate. */
@Repository
class JdbcAdminProductReader implements AdminProductReader {

    private final JdbcTemplate jdbc;

    JdbcAdminProductReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Metadata> findMetadata(ProductId id) {
        return jdbc
                .query(
                        "select created_at, updated_at from catalog_products where id = ?",
                        (result, row) -> new Metadata(
                                result.getTimestamp("created_at").toInstant(),
                                result.getTimestamp("updated_at").toInstant()),
                        id.value())
                .stream()
                .findFirst();
    }
}
