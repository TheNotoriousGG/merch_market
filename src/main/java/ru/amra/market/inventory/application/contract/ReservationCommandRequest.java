package ru.amra.market.inventory.application.contract;

import java.util.UUID;

/** Caller-scoped idempotent reservation lifecycle command. */
public record ReservationCommandRequest(UUID ownerReference, UUID reservationId, String idempotencyKey) {}
