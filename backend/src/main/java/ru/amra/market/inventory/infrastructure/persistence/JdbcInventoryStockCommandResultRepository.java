package ru.amra.market.inventory.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.port.InventoryStockCommandResultRepository;
import ru.amra.market.inventory.application.port.InventoryStockCommandResultRepository.StockCommandResult;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryBalance;
import ru.amra.market.inventory.domain.InventoryMovement;
import ru.amra.market.inventory.domain.InventoryMovementType;
import ru.amra.market.inventory.domain.InventoryMutation;
import ru.amra.market.inventory.domain.MovementId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.MovementReference;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** JDBC immutable warehouse command response store. */
@Repository
class JdbcInventoryStockCommandResultRepository implements InventoryStockCommandResultRepository {
    private final JdbcTemplate jdbc;

    JdbcInventoryStockCommandResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(InventoryMutation mutation, Instant balanceUpdatedAt) {
        var balance = mutation.balance();
        jdbc.update(
                """
                insert into inventory_stock_command_results (
                    movement_id, warehouse_id, variant_id, on_hand, reserved,
                    balance_version, balance_updated_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                mutation.movement().id().value(),
                balance.warehouseId().value(),
                balance.variantId().value(),
                balance.onHand().value(),
                balance.reserved().value(),
                balance.version(),
                Timestamp.from(balanceUpdatedAt));
    }

    @Override
    public Optional<StockCommandResult> find(MovementId movementId) {
        return jdbc
                .query(
                        """
                        select result.warehouse_id, result.variant_id, result.on_hand, result.reserved,
                               result.balance_version, result.balance_updated_at,
                               movement.movement_type, movement.quantity_delta,
                               movement.reason, movement.reference, movement.occurred_at
                        from inventory_stock_command_results result
                        join inventory_movements movement on movement.id = result.movement_id
                        where result.movement_id = ?
                        """,
                        (result, row) -> {
                            var warehouse = new WarehouseId(result.getObject("warehouse_id", java.util.UUID.class));
                            var variant = new CatalogVariantId(result.getObject("variant_id", java.util.UUID.class));
                            var balance = InventoryBalance.restore(
                                    warehouse,
                                    variant,
                                    new StockQuantity(result.getLong("on_hand")),
                                    new StockQuantity(result.getLong("reserved")),
                                    result.getLong("balance_version"));
                            var reference = result.getString("reference");
                            var movement = new InventoryMovement(
                                    movementId,
                                    warehouse,
                                    variant,
                                    InventoryMovementType.valueOf(result.getString("movement_type")),
                                    result.getLong("quantity_delta"),
                                    new MovementReason(result.getString("reason")),
                                    reference == null ? null : new MovementReference(reference),
                                    result.getTimestamp("occurred_at").toInstant());
                            return new StockCommandResult(
                                    new InventoryMutation(balance, movement),
                                    result.getTimestamp("balance_updated_at").toInstant());
                        },
                        movementId.value())
                .stream()
                .findFirst();
    }
}
