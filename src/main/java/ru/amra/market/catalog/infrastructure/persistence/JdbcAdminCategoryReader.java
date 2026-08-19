package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.port.AdminCategoryReader;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryStatus;

/** Administrative category projection adapter. */
@Repository
class JdbcAdminCategoryReader implements AdminCategoryReader {

    private final JdbcTemplate jdbc;

    JdbcAdminCategoryReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Optional<Snapshot> findById(CategoryId id) {
        var rows = jdbc.query(
                """
                select id, parent_id, slug, name, display_order, status, version, updated_at
                from catalog_categories
                where id = ?
                """,
                (resultSet, rowNumber) -> new Snapshot(
                        new CategoryId(requireNonNull(resultSet.getObject("id", UUID.class))),
                        parent(resultSet.getObject("parent_id", UUID.class)),
                        requireNonNull(resultSet.getString("slug")),
                        requireNonNull(resultSet.getString("name")),
                        resultSet.getInt("display_order"),
                        CategoryStatus.valueOf(requireNonNull(resultSet.getString("status"))),
                        resultSet.getLong("version"),
                        requireNonNull(resultSet.getTimestamp("updated_at")).toInstant()),
                id.value());
        return rows.stream().findFirst();
    }

    private static @org.jspecify.annotations.Nullable CategoryId parent(
            @org.jspecify.annotations.Nullable UUID parentId) {
        return parentId == null ? null : new CategoryId(parentId);
    }
}
