package ru.amra.market.catalog.application.port;

import ru.amra.market.catalog.application.CatalogAuditContext;

/** Supplies authenticated actor and correlation metadata at an administrative boundary. */
public interface CatalogAuditContextProvider {

    CatalogAuditContext current();
}
