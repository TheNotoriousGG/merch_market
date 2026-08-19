package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.InventoryAdminBalanceView;
import ru.amra.market.inventory.application.port.InventoryAdminBalanceReader;

/** JDBC projection for the configured primary warehouse and exact balances. */
@Repository
class JdbcInventoryAdminBalanceReader implements InventoryAdminBalanceReader {
    private final JdbcTemplate jdbc;

    JdbcInventoryAdminBalanceReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID primaryWarehouseId() {
        return requireNonNull(jdbc.queryForObject(
                "select id from inventory_warehouses where code = 'PRIMARY' and status = 'ACTIVE'", UUID.class));
    }

    @Override
    public Optional<InventoryAdminBalanceView> findPrimary(UUID variantId) {
        return jdbc
                .query(
                        """
                        select warehouse.code, balance.warehouse_id, balance.variant_id,
                               balance.on_hand, balance.reserved, balance.version, balance.updated_at
                        from inventory_balances balance
                        join inventory_warehouses warehouse on warehouse.id = balance.warehouse_id
                        where warehouse.code = 'PRIMARY' and balance.variant_id = ?
                        """,
                        (result, row) -> new InventoryAdminBalanceView(
                                result.getString("code"),
                                result.getObject("warehouse_id", UUID.class),
                                result.getObject("variant_id", UUID.class),
                                result.getLong("on_hand"),
                                result.getLong("reserved"),
                                result.getLong("version"),
                                result.getTimestamp("updated_at").toInstant()),
                        variantId)
                .stream()
                .findFirst();
    }
}
