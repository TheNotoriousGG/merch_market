package ru.amra.market.inventory.domain;

/** Immutable reservation lifecycle event type. */
public enum ReservationEventType {
    CREATED,
    EXTENDED,
    COMMITTED,
    RELEASED,
    EXPIRED
}
