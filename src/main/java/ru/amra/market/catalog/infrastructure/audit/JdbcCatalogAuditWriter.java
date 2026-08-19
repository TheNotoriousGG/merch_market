package ru.amra.market.catalog.infrastructure.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.CatalogAuditEvent;
import ru.amra.market.catalog.application.port.CatalogAuditWriter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL append-only adapter for catalog audit events. */
@Repository
class JdbcCatalogAuditWriter implements CatalogAuditWriter {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcCatalogAuditWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(CatalogAuditEvent event) {
        try {
            jdbc.update(
                    """
                    insert into catalog_audit_events (
                        actor_scope, entity_type, entity_id, action, reason,
                        correlation_id, safe_diff
                    ) values (?, ?, ?, ?, ?, ?, ?::jsonb)
                    """,
                    event.context().actorScope(),
                    event.entityType(),
                    event.entityId(),
                    event.action(),
                    event.reason(),
                    event.context().correlationId(),
                    objectMapper.writeValueAsString(event.safeDiff()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Safe catalog audit diff must be JSON serializable", exception);
        }
    }
}
