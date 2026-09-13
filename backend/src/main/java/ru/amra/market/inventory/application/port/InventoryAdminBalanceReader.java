package ru.amra.market.inventory.application.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ru.amra.market.inventory.application.InventoryAdminBalanceView;

/** Exact configured-warehouse balance projection for protected administration. */
public interface InventoryAdminBalanceReader {
    /** Returns the configured primary warehouse identity. */
    UUID primaryWarehouseId();

    /** Finds the primary warehouse balance for a catalog variant. */
    Optional<InventoryAdminBalanceView> findPrimary(UUID variantId);

    /** Reads existing primary balances for the supplied variants; missing balances remain zero. */
    Map<UUID, InventoryAdminBalanceView> findPrimary(Set<UUID> variantIds);

    /** Returns the newest immutable physical movements for the primary warehouse. */
    List<MovementView> latestMovements(int limit);

    record MovementView(
            UUID id,
            UUID variantId,
            String type,
            long quantityDelta,
            String reason,
            @org.jspecify.annotations.Nullable String reference,
            java.time.Instant occurredAt) {}
}
