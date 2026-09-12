package ru.amra.market.inventory.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.StaleInventoryVersionException;
import ru.amra.market.inventory.application.port.InventoryStockRepository;
import ru.amra.market.inventory.application.port.InventoryStockRepository.InventoryBalanceKey;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryBalance;
import ru.amra.market.inventory.domain.InventoryMovement;
import ru.amra.market.inventory.domain.InventoryMutation;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** JDBC adapter keeping exact balance and immutable physical ledger in one transaction. */
@Repository
class JdbcInventoryStockRepository implements InventoryStockRepository {

    private static final String SELECT_BALANCE = """
            select warehouse_id, variant_id, on_hand, reserved, version
            from inventory_balances
            where warehouse_id = ? and variant_id = ?
            """;

    private final JdbcTemplate jdbc;

    JdbcInventoryStockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InventoryBalance lockOrCreate(WarehouseId warehouseId, CatalogVariantId variantId) {
        jdbc.update("""
                insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved, version)
                values (?, ?, 0, 0, 0)
                on conflict (warehouse_id, variant_id) do nothing
                """, warehouseId.value(), variantId.value());
        return lock(warehouseId, variantId).orElseThrow();
    }

    @Override
    public List<InventoryBalance> lockOrCreateAll(List<InventoryBalanceKey> keys) {
        var ordered = keys.stream().distinct().sorted().toList();
        for (var key : ordered) {
            jdbc.update("""
                    insert into inventory_balances (warehouse_id, variant_id, on_hand, reserved, version)
                    values (?, ?, 0, 0, 0)
                    on conflict (warehouse_id, variant_id) do nothing
                    """, key.warehouseId().value(), key.variantId().value());
        }
        return ordered.stream()
                .map(key -> lock(key.warehouseId(), key.variantId()).orElseThrow())
                .toList();
    }

    @Override
    public List<InventoryBalance> lockAll(List<InventoryBalanceKey> keys) {
        return keys.stream()
                .distinct()
                .sorted()
                .map(key -> lock(key.warehouseId(), key.variantId()).orElseThrow())
                .toList();
    }

    @Override
    public Optional<InventoryBalance> lock(WarehouseId warehouseId, CatalogVariantId variantId) {
        return queryOne(SELECT_BALANCE + " for update", warehouseId, variantId);
    }

    @Override
    public Optional<InventoryBalance> find(WarehouseId warehouseId, CatalogVariantId variantId) {
        return queryOne(SELECT_BALANCE, warehouseId, variantId);
    }

    @Override
    public void save(InventoryMutation mutation) {
        var balance = mutation.balance();
        updateBalance(balance, mutation.movement().occurredAt());
        insertMovement(mutation.movement(), null);
    }

    @Override
    public void saveReservationCommit(InventoryMutation mutation, ReservationId reservationId) {
        updateBalance(mutation.balance(), mutation.movement().occurredAt());
        insertMovement(mutation.movement(), reservationId);
    }

    @Override
    public void saveBalance(InventoryBalance balance, java.time.Instant updatedAt) {
        updateBalance(balance, updatedAt);
    }

    private void updateBalance(InventoryBalance balance, java.time.Instant updatedAt) {
        var expectedVersion = balance.version() - 1;
        var updated = jdbc.update(
                """
                update inventory_balances
                set on_hand = ?, reserved = ?, version = ?, updated_at = ?
                where warehouse_id = ? and variant_id = ? and version = ?
                """,
                balance.onHand().value(),
                balance.reserved().value(),
                balance.version(),
                Timestamp.from(updatedAt),
                balance.warehouseId().value(),
                balance.variantId().value(),
                expectedVersion);
        if (updated != 1) {
            throw new StaleInventoryVersionException();
        }
    }

    @Override
    public long physicalTotal(WarehouseId warehouseId, CatalogVariantId variantId) {
        var total = jdbc.queryForObject("""
                select coalesce(sum(quantity_delta), 0)::bigint
                from inventory_movements
                where warehouse_id = ? and variant_id = ?
                """, Long.class, warehouseId.value(), variantId.value());
        return total == null ? 0 : total;
    }

    private Optional<InventoryBalance> queryOne(String sql, WarehouseId warehouseId, CatalogVariantId variantId) {
        return jdbc
                .query(sql, JdbcInventoryStockRepository::mapBalance, warehouseId.value(), variantId.value())
                .stream()
                .findFirst();
    }

    private void insertMovement(InventoryMovement movement, @Nullable ReservationId reservation) {
        jdbc.update(
                """
                insert into inventory_movements (
                    id, warehouse_id, variant_id, movement_type, quantity_delta,
                    reason, reference, reservation_id, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                movement.id().value(),
                movement.warehouseId().value(),
                movement.variantId().value(),
                movement.type().name(),
                movement.quantityDelta(),
                movement.reason().value(),
                movement.reference() == null ? null : movement.reference().value(),
                reservation == null ? null : reservation.value(),
                Timestamp.from(movement.occurredAt()));
    }

    private static InventoryBalance mapBalance(ResultSet result, int rowNumber) throws SQLException {
        return InventoryBalance.restore(
                new WarehouseId(result.getObject("warehouse_id", java.util.UUID.class)),
                new CatalogVariantId(result.getObject("variant_id", java.util.UUID.class)),
                new StockQuantity(result.getLong("on_hand")),
                new StockQuantity(result.getLong("reserved")),
                result.getLong("version"));
    }
}
