package ru.amra.market.inventory.domain;

/** Lifecycle state of an all-or-nothing inventory reservation. */
public enum ReservationStatus {
    ACTIVE,
    COMMITTED,
    RELEASED,
    EXPIRED;

    /** Returns whether no further state change is allowed. */
    public boolean isTerminal() {
        return this != ACTIVE;
    }
}
