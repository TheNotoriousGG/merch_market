package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.CatalogIdempotencyConflictException;
import ru.amra.market.catalog.application.port.CatalogIdempotencyStore;

/** Transactional PostgreSQL claim store for caller-scoped catalog command deduplication. */
@Repository
class JdbcCatalogIdempotencyStore implements CatalogIdempotencyStore {

    private final JdbcTemplate jdbc;

    JdbcCatalogIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> claim(String actorScope, String key, String operation, String requestFingerprint) {
        var inserted = jdbc.update("""
                insert into catalog_command_idempotency (
                    actor_scope, idempotency_key, operation, request_fingerprint
                ) values (?, ?, ?, ?)
                on conflict (actor_scope, idempotency_key) do nothing
                """, actorScope, key, operation, requestFingerprint);
        if (inserted == 1) {
            return Optional.empty();
        }
        var existing = requireNonNull(jdbc.queryForObject(
                """
                select operation, request_fingerprint, resource_id
                from catalog_command_idempotency
                where actor_scope = ? and idempotency_key = ?
                """,
                (resultSet, rowNumber) -> new StoredClaim(
                        requireNonNull(resultSet.getString("operation")),
                        requireNonNull(resultSet.getString("request_fingerprint")),
                        resultSet.getObject("resource_id", UUID.class)),
                actorScope,
                key));
        if (!existing.operation().equals(operation) || !existing.fingerprint().equals(requestFingerprint)) {
            throw new CatalogIdempotencyConflictException();
        }
        return Optional.of(requireNonNull(existing.resourceId()));
    }

    @Override
    public void complete(String actorScope, String key, UUID resourceId) {
        var updated = jdbc.update("""
                update catalog_command_idempotency
                set resource_id = ?, completed_at = current_timestamp
                where actor_scope = ? and idempotency_key = ? and resource_id is null
                """, resourceId, actorScope, key);
        if (updated != 1) {
            throw new IllegalStateException("Catalog idempotency claim cannot be completed more than once");
        }
    }

    private record StoredClaim(
            String operation, String fingerprint, @Nullable UUID resourceId) {}
}
