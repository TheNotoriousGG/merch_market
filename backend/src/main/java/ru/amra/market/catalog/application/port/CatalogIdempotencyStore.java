package ru.amra.market.catalog.application.port;

import java.util.Optional;
import java.util.UUID;

/** Durable caller-scoped deduplication boundary for catalog create commands. */
public interface CatalogIdempotencyStore {

    /** Claims a key or returns the resource produced by an identical completed command. */
    Optional<UUID> claim(String actorScope, String key, String operation, String requestFingerprint);

    /** Atomically associates the claimed command with its created resource. */
    void complete(String actorScope, String key, UUID resourceId);
}
