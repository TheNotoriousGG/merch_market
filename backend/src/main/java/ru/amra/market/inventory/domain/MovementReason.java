package ru.amra.market.inventory.domain;

/** Bounded human-entered explanation retained in warehouse history and safe audit. */
public record MovementReason(String value) {

    private static final int MAX_LENGTH = 500;

    /** Creates a trimmed reason containing 3-500 characters. */
    public MovementReason {
        if (value == null || value.strip().length() < 3 || value.strip().length() > MAX_LENGTH) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_REASON, "Movement reason must contain 3-500 characters");
        }
        value = value.strip();
    }
}
