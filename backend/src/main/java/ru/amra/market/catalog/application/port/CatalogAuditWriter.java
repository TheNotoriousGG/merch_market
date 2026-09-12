package ru.amra.market.catalog.application.port;

import ru.amra.market.catalog.application.CatalogAuditEvent;

/** Append-only persistence boundary for administrative catalog changes. */
public interface CatalogAuditWriter {

    void append(CatalogAuditEvent event);
}
