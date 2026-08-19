package ru.amra.market.catalog.application;

import java.util.Map;
import java.util.UUID;

/** Append-only, non-sensitive catalog change event. */
public record CatalogAuditEvent(
        CatalogAuditContext context,
        String entityType,
        UUID entityId,
        String action,
        String reason,
        Map<String, String> safeDiff) {

    public CatalogAuditEvent {
        safeDiff = Map.copyOf(safeDiff);
    }
}
