package ru.amra.market.inventory.application;

/** Authenticated actor and request correlation metadata for inventory audit. */
public record InventoryAuditContext(String actorScope, String correlationId) {}
