package ru.amra.market.inventory.application.port;

import java.util.Optional;
import java.util.UUID;

/** Durable transaction-bound inventory command deduplication. */
public interface InventoryIdempotencyStore {
    /** Claims a key or returns the resource created by an identical command. */
    Optional<UUID> claim(String actorScope, String key, String operation, String fingerprint);

    /** Completes a claim with the created resource. */
    void complete(String actorScope, String key, UUID resourceId);
}
