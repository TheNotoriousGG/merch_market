package ru.amra.market.inventory.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.InventoryIdempotencyConflictException;
import ru.amra.market.inventory.application.port.InventoryIdempotencyStore;

/** PostgreSQL claim store for caller-scoped inventory command deduplication. */
@Repository
class JdbcInventoryIdempotencyStore implements InventoryIdempotencyStore {
    private final JdbcTemplate jdbc;

    JdbcInventoryIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> claim(String actorScope, String key, String operation, String fingerprint) {
        var inserted = jdbc.update("""
                insert into inventory_command_idempotency (
                    actor_scope, idempotency_key, operation, request_fingerprint
                ) values (?, ?, ?, ?)
                on conflict (actor_scope, idempotency_key) do nothing
                """, actorScope, key, operation, fingerprint);
        if (inserted == 1) {
            return Optional.empty();
        }
        var stored = requireNonNull(jdbc.queryForObject(
                """
                select operation, request_fingerprint, resource_id
                from inventory_command_idempotency
                where actor_scope = ? and idempotency_key = ?
                """,
                (result, row) -> new Claim(
                        requireNonNull(result.getString("operation")),
                        requireNonNull(result.getString("request_fingerprint")),
                        result.getObject("resource_id", UUID.class)),
                actorScope,
                key));
        if (!stored.operation().equals(operation) || !stored.fingerprint().equals(fingerprint)) {
            throw new InventoryIdempotencyConflictException();
        }
        return Optional.of(requireNonNull(stored.resourceId()));
    }

    @Override
    public void complete(String actorScope, String key, UUID resourceId) {
        var updated = jdbc.update("""
                update inventory_command_idempotency
                set resource_id = ?, completed_at = current_timestamp
                where actor_scope = ? and idempotency_key = ? and resource_id is null
                """, resourceId, actorScope, key);
        if (updated != 1) {
            throw new IllegalStateException("Inventory idempotency claim was already completed");
        }
    }

    private record Claim(
            String operation, String fingerprint, @Nullable UUID resourceId) {}
}
