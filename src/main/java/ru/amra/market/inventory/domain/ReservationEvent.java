package ru.amra.market.inventory.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Immutable evidence of one reservation lifecycle transition. */
public record ReservationEvent(
        ReservationEventId id,
        ReservationId reservationId,
        ReservationEventType type,
        @Nullable ReservationStatus previousStatus,
        ReservationStatus currentStatus,
        @Nullable Instant previousExpiresAt,
        Instant currentExpiresAt,
        Instant occurredAt) {

    /** Validates event metadata and transition shape. */
    public ReservationEvent {
        if (id == null
                || reservationId == null
                || type == null
                || currentStatus == null
                || currentExpiresAt == null
                || occurredAt == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_RESERVATION, "Reservation event metadata must not be null");
        }
        if (type == ReservationEventType.CREATED && previousStatus != null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_RESERVATION, "Created event must not have a previous status");
        }
        if (type != ReservationEventType.CREATED && previousStatus == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_RESERVATION, "Transition event must have a previous status");
        }
    }
}
