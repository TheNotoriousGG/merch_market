package ru.amra.market.catalog.application;

/** Safe actor and request correlation metadata attached to one administrative command. */
public record CatalogAuditContext(String actorScope, String correlationId) {}
