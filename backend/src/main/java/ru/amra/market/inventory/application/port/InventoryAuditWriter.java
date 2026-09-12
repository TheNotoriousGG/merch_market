package ru.amra.market.inventory.application.port;

import ru.amra.market.inventory.application.InventoryAuditEvent;

/** Append-only inventory audit persistence boundary. */
public interface InventoryAuditWriter {
    /** Appends one successful warehouse mutation event in the caller transaction. */
    void append(InventoryAuditEvent event);
}
