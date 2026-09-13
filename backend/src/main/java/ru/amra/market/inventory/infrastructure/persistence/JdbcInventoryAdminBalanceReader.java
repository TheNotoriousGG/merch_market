package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.InventoryAdminBalanceView;
import ru.amra.market.inventory.application.port.InventoryAdminBalanceReader;

/** JDBC projection for the configured primary warehouse and exact balances. */
@Repository
class JdbcInventoryAdminBalanceReader implements InventoryAdminBalanceReader {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    JdbcInventoryAdminBalanceReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
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

    @Override
    public Map<UUID, InventoryAdminBalanceView> findPrimary(Set<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        var balances = new LinkedHashMap<UUID, InventoryAdminBalanceView>();
        namedJdbc.query("""
                select warehouse.code, balance.warehouse_id, balance.variant_id,
                       balance.on_hand, balance.reserved, balance.version, balance.updated_at
                from inventory_balances balance
                join inventory_warehouses warehouse on warehouse.id = balance.warehouse_id
                where warehouse.code = 'PRIMARY' and balance.variant_id in (:variantIds)
                """, new MapSqlParameterSource("variantIds", variantIds), result -> {
            var view = mapBalance(result);
            balances.put(view.variantId(), view);
        });
        return Map.copyOf(balances);
    }

    @Override
    public List<MovementView> latestMovements(int limit) {
        return jdbc.query(
                """
                select movement.id, movement.variant_id, movement.movement_type,
                       movement.quantity_delta, movement.reason, movement.reference, movement.occurred_at
                from inventory_movements movement
                join inventory_warehouses warehouse on warehouse.id = movement.warehouse_id
                where warehouse.code = 'PRIMARY'
                order by movement.occurred_at desc, movement.id desc
                limit ?
                """,
                (result, row) -> new MovementView(
                        result.getObject("id", UUID.class),
                        result.getObject("variant_id", UUID.class),
                        result.getString("movement_type"),
                        result.getLong("quantity_delta"),
                        result.getString("reason"),
                        result.getString("reference"),
                        result.getTimestamp("occurred_at").toInstant()),
                limit);
    }

    @Override
    public List<MovementView> movementPage(int limit, @Nullable Instant beforeAt, @Nullable UUID beforeId) {
        var parameters = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("beforeAt", beforeAt == null ? null : java.sql.Timestamp.from(beforeAt))
                .addValue("beforeId", beforeId);
        return namedJdbc.query(
                """
                select movement.id, movement.variant_id, movement.movement_type,
                       movement.quantity_delta, movement.reason, movement.reference, movement.occurred_at
                from inventory_movements movement
                join inventory_warehouses warehouse on warehouse.id = movement.warehouse_id
                where warehouse.code = 'PRIMARY'
                  and (cast(:beforeAt as timestamptz) is null
                       or (movement.occurred_at, movement.id)
                          < (cast(:beforeAt as timestamptz), cast(:beforeId as uuid)))
                order by movement.occurred_at desc, movement.id desc
                limit :limit
                """,
                parameters,
                (result, row) -> new MovementView(
                        result.getObject("id", UUID.class),
                        result.getObject("variant_id", UUID.class),
                        result.getString("movement_type"),
                        result.getLong("quantity_delta"),
                        result.getString("reason"),
                        result.getString("reference"),
                        result.getTimestamp("occurred_at").toInstant()));
    }

    private static InventoryAdminBalanceView mapBalance(java.sql.ResultSet result) throws java.sql.SQLException {
        return new InventoryAdminBalanceView(
                result.getString("code"),
                result.getObject("warehouse_id", UUID.class),
                result.getObject("variant_id", UUID.class),
                result.getLong("on_hand"),
                result.getLong("reserved"),
                result.getLong("version"),
                result.getTimestamp("updated_at").toInstant());
    }
}
