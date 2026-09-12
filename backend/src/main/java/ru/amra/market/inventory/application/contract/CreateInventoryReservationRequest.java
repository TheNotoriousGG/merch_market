package ru.amra.market.inventory.application.contract;

import java.util.List;
import java.util.UUID;

/** Idempotent internal all-or-nothing reservation request. */
public record CreateInventoryReservationRequest(
        UUID ownerReference, String idempotencyKey, List<ReservationRequestLine> lines) {}
