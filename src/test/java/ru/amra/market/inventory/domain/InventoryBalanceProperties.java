package ru.amra.market.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

class InventoryBalanceProperties {

    private static final WarehouseId WAREHOUSE_ID = new WarehouseId(new UUID(0, 1));
    private static final CatalogVariantId VARIANT_ID = new CatalogVariantId(new UUID(0, 2));
    private static final MovementId MOVEMENT_ID = new MovementId(new UUID(0, 3));
    private static final MovementReason REASON = new MovementReason("Property test movement");

    @Property
    void reserveThenReleasePreservesQuantities(
            @ForAll @LongRange(min = 1, max = 1_000_000) long onHand,
            @ForAll @LongRange(min = 0, max = 1_000_000) long selector) {
        var quantity = new StockQuantity(1 + selector % onHand);
        var original =
                InventoryBalance.restore(WAREHOUSE_ID, VARIANT_ID, new StockQuantity(onHand), StockQuantity.ZERO, 10);

        var result = original.reserve(quantity).release(quantity);

        assertThat(result.onHand()).isEqualTo(original.onHand());
        assertThat(result.reserved()).isEqualTo(original.reserved());
        assertThat(result.available()).isEqualTo(original.available());
        assertThat(result.version()).isEqualTo(original.version() + 2);
    }

    @Property
    void reconciliationNeverProducesNegativeAvailability(
            @ForAll @LongRange(min = 0, max = 1_000_000) long reserved,
            @ForAll @LongRange(min = 1, max = 1_000_000) long extra,
            @ForAll boolean increase) {
        var originalOnHand = reserved + extra;
        var target = increase ? originalOnHand + 1 : reserved;
        var original = InventoryBalance.restore(
                WAREHOUSE_ID, VARIANT_ID, new StockQuantity(originalOnHand), new StockQuantity(reserved), 0);

        var result = original.reconcile(MOVEMENT_ID, new StockQuantity(target), REASON, null, Instant.EPOCH);

        assertThat(result.balance().available().value()).isGreaterThanOrEqualTo(0);
        assertThat(result.balance().reserved().value())
                .isLessThanOrEqualTo(result.balance().onHand().value());
    }

    @Property
    void receiptMovementAlwaysMatchesBalanceDelta(
            @ForAll @LongRange(min = 0, max = 1_000_000) long initial,
            @ForAll @LongRange(min = 1, max = 1_000_000) long receipt) {
        var original =
                InventoryBalance.restore(WAREHOUSE_ID, VARIANT_ID, new StockQuantity(initial), StockQuantity.ZERO, 0);

        var result = original.receive(MOVEMENT_ID, new StockQuantity(receipt), REASON, null, Instant.EPOCH);

        assertThat(result.balance().onHand().value() - original.onHand().value())
                .isEqualTo(result.movement().quantityDelta());
        assertThat(result.balance().available()).isEqualTo(result.balance().onHand());
    }
}
