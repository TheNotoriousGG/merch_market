package ru.amra.market.inventory.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** De-duplicated immutable reservation lines in first-seen order. */
public final class ReservationLines {

    public static final int MAX_DISTINCT_LINES = 100;

    private final List<ReservationLine> values;

    private ReservationLines(List<ReservationLine> values) {
        this.values = values;
    }

    /** Aggregates duplicate warehouse/variant lines with checked quantity addition. */
    public static ReservationLines of(@Nullable List<ReservationLine> input) {
        if (input == null || input.isEmpty()) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.INVALID_RESERVATION_LINES, "Reservation must contain at least one line");
        }
        var aggregated = new LinkedHashMap<LineKey, StockQuantity>();
        for (var line : input) {
            if (line == null) {
                throw new InventoryInvariantViolation(
                        InventoryInvariant.INVALID_RESERVATION_LINES, "Reservation lines must not contain null");
            }
            var key = new LineKey(line.warehouseId(), line.variantId());
            aggregated.merge(key, line.quantity(), StockQuantity::plus);
            if (aggregated.size() > MAX_DISTINCT_LINES) {
                throw new InventoryInvariantViolation(
                        InventoryInvariant.RESERVATION_LINE_LIMIT_EXCEEDED,
                        "Reservation must not exceed 100 distinct lines");
            }
        }
        var normalized = new ArrayList<ReservationLine>(aggregated.size());
        aggregated.forEach(
                (key, quantity) -> normalized.add(new ReservationLine(key.warehouseId(), key.variantId(), quantity)));
        return new ReservationLines(List.copyOf(normalized));
    }

    /** Returns immutable normalized lines. */
    public List<ReservationLine> values() {
        return values;
    }

    /** Returns the distinct line count. */
    public int size() {
        return values.size();
    }

    private record LineKey(WarehouseId warehouseId, CatalogVariantId variantId) {}
}
