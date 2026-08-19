package ru.amra.market.inventory.domain;

/** Approved physical causes and expected delta direction. */
public enum InventoryMovementType {
    RECEIPT(1),
    ADJUSTMENT_INCREASE(1),
    ADJUSTMENT_DECREASE(-1),
    RESERVATION_COMMIT(-1),
    RETURN(1);

    private final int direction;

    InventoryMovementType(int direction) {
        this.direction = direction;
    }

    boolean accepts(long delta) {
        return Long.signum(delta) == direction;
    }
}
