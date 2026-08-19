package ru.amra.market.inventory.application.port;

/** Supplies the authenticated non-PII actor scope for administrative idempotency. */
public interface InventoryActorScopeProvider {
    /** Returns the stable authenticated subject for the current request. */
    String currentActorScope();
}
