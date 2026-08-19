package ru.amra.market.inventory.domain;

/** One positive warehouse/variant quantity in a reservation. */
public record ReservationLine(WarehouseId warehouseId, CatalogVariantId variantId, StockQuantity quantity) {

    /** Validates the line identity and positive quantity. */
    public ReservationLine {
        if (warehouseId == null || variantId == null || quantity == null) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_RESERVATION_LINES,
                    "Reservation line identity and quantity must not be null");
        }
        quantity.requirePositive();
    }
}
