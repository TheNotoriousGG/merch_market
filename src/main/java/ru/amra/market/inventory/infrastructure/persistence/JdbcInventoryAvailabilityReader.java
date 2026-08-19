package ru.amra.market.inventory.infrastructure.persistence;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.port.InventoryAvailabilityReader;

/** Single bounded aggregate query for positive availability across warehouses. */
@Repository
class JdbcInventoryAvailabilityReader implements InventoryAvailabilityReader {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcInventoryAvailabilityReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> findInStock(Set<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(jdbc.queryForList("""
                select variant_id
                from inventory_balances
                where variant_id in (:ids)
                group by variant_id
                having sum(on_hand - reserved) > 0
                """, Map.of("ids", variantIds), UUID.class));
    }
}
