package ru.amra.market.inventory.infrastructure.audit;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.InventoryAuditEvent;
import ru.amra.market.inventory.application.port.InventoryAuditWriter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL append-only adapter for inventory audit events. */
@Repository
class JdbcInventoryAuditWriter implements InventoryAuditWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcInventoryAuditWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(InventoryAuditEvent event) {
        try {
            jdbc.update(
                    """
                    insert into inventory_audit_events (
                        actor_scope, occurred_at, warehouse_id, variant_id, action,
                        reason, reference, correlation_id, safe_diff
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """,
                    event.context().actorScope(),
                    Timestamp.from(event.occurredAt()),
                    event.warehouseId().value(),
                    event.variantId().value(),
                    event.action(),
                    event.reason().value(),
                    event.reference() == null ? null : event.reference().value(),
                    event.context().correlationId(),
                    objectMapper.writeValueAsString(event.safeDiff()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Safe inventory audit diff must be JSON serializable", exception);
        }
    }
}
