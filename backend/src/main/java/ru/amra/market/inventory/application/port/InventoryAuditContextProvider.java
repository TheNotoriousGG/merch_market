package ru.amra.market.inventory.application.port;

import ru.amra.market.inventory.application.InventoryAuditContext;

/** Supplies authenticated request metadata without coupling application code to Spring Security. */
public interface InventoryAuditContextProvider {
    /** Returns current actor scope and correlation id. */
    InventoryAuditContext current();
}
