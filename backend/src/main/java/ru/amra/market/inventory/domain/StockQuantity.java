package ru.amra.market.inventory.domain;

/** Non-negative whole-unit stock quantity with checked arithmetic. */
public record StockQuantity(long value) implements Comparable<StockQuantity> {

    public static final StockQuantity ZERO = new StockQuantity(0);

    /** Creates a non-negative quantity. */
    public StockQuantity {
        if (value < 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_QUANTITY, "Stock quantity must not be negative");
        }
    }

    /** Returns the checked sum. */
    public StockQuantity plus(StockQuantity other) {
        try {
            return new StockQuantity(Math.addExact(value, other.value));
        } catch (ArithmeticException exception) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.QUANTITY_OVERFLOW, "Stock quantity addition overflowed");
        }
    }

    /** Returns the non-negative difference. */
    public StockQuantity minus(StockQuantity other, InventoryInvariant underflowInvariant) {
        if (other.value > value) {
            throw new InventoryInvariantViolation(underflowInvariant, "Stock quantity would become negative");
        }
        return new StockQuantity(value - other.value);
    }

    /** Rejects zero for commands that require a positive quantity. */
    public StockQuantity requirePositive() {
        if (value == 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.NON_POSITIVE_QUANTITY, "Command quantity must be positive");
        }
        return this;
    }

    @Override
    public int compareTo(StockQuantity other) {
        return Long.compare(value, other.value);
    }
}
