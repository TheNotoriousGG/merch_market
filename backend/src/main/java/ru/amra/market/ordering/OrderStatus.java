package ru.amra.market.ordering;

import java.util.EnumSet;

/** Explicit order lifecycle with fail-closed transition validation. */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    /** Returns the requested status when the transition is allowed. */
    public OrderStatus transitionTo(OrderStatus target) {
        var allowed =
                switch (this) {
                    case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
                    case CONFIRMED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
                };
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Order transition " + this + " -> " + target + " is not allowed");
        }
        return target;
    }
}
