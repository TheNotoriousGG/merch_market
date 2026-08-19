package ru.amra.market.catalog.infrastructure.persistence;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.port.ActiveCategoryReader;
import ru.amra.market.catalog.domain.CategoryId;

/** Set-based active category lookup for publication checks. */
@Repository
class JdbcActiveCategoryReader implements ActiveCategoryReader {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcActiveCategoryReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<CategoryId> findActive(Set<CategoryId> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return jdbc
                .query(
                        "select id from catalog_categories where status = 'ACTIVE' and id in (:ids)",
                        new MapSqlParameterSource(
                                "ids",
                                candidates.stream().map(CategoryId::value).toList()),
                        (result, row) -> new CategoryId(result.getObject("id", UUID.class)))
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }
}
