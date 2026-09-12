package ru.amra.market.inventory.domain;

/** Optional bounded warehouse document or future order reference without customer data. */
public record MovementReference(String value) {

    private static final int MAX_LENGTH = 120;

    /** Creates a trimmed nonblank reference. */
    public MovementReference {
        if (value == null || value.isBlank() || value.strip().length() > MAX_LENGTH) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_REFERENCE, "Movement reference must contain 1-120 characters");
        }
        value = value.strip();
    }
}
