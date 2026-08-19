package ru.amra.market.inventory.application.port;

import java.time.Instant;
import java.util.Optional;
import ru.amra.market.inventory.domain.InventoryMutation;
import ru.amra.market.inventory.domain.MovementId;

/** Immutable response snapshots for exact idempotent warehouse command replay. */
public interface InventoryStockCommandResultRepository {
    /** Stores the command result in the same transaction as balance and movement. */
    void insert(InventoryMutation mutation, Instant balanceUpdatedAt);

    /** Restores the original result instead of composing it from mutable current state. */
    Optional<StockCommandResult> find(MovementId movementId);

    /** Immutable replay value containing domain result and its balance representation time. */
    record StockCommandResult(InventoryMutation mutation, Instant balanceUpdatedAt) {}
}
