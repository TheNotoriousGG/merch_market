package ru.amra.market.inventory.application.port;

import java.util.Optional;
import java.util.UUID;
import ru.amra.market.inventory.application.InventoryAdminBalanceView;

/** Exact configured-warehouse balance projection for protected administration. */
public interface InventoryAdminBalanceReader {
    /** Returns the configured primary warehouse identity. */
    UUID primaryWarehouseId();

    /** Finds the primary warehouse balance for a catalog variant. */
    Optional<InventoryAdminBalanceView> findPrimary(UUID variantId);
}
