package ru.amra.market.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryBalanceTest {

    private static final WarehouseId WAREHOUSE_ID = new WarehouseId(new UUID(0, 1));
    private static final CatalogVariantId VARIANT_ID = new CatalogVariantId(new UUID(0, 2));
    private static final MovementId MOVEMENT_ID = new MovementId(new UUID(0, 3));
    private static final MovementReason REASON = new MovementReason("Initial warehouse receipt");
    private static final MovementReference REFERENCE = new MovementReference("RECEIPT-42");
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    void emptyBalanceStartsWithNoAvailabilityAndVersionZero() {
        var balance = InventoryBalance.empty(WAREHOUSE_ID, VARIANT_ID);

        assertThat(balance.onHand()).isEqualTo(StockQuantity.ZERO);
        assertThat(balance.reserved()).isEqualTo(StockQuantity.ZERO);
        assertThat(balance.available()).isEqualTo(StockQuantity.ZERO);
        assertThat(balance.version()).isZero();
    }

    @Test
    void receiptReturnsNewBalanceAndMatchingImmutableMovement() {
        var original = InventoryBalance.empty(WAREHOUSE_ID, VARIANT_ID);

        var mutation = original.receive(MOVEMENT_ID, new StockQuantity(12), REASON, REFERENCE, NOW);

        assertThat(original.onHand()).isEqualTo(StockQuantity.ZERO);
        assertThat(mutation.balance().onHand()).isEqualTo(new StockQuantity(12));
        assertThat(mutation.balance().available()).isEqualTo(new StockQuantity(12));
        assertThat(mutation.balance().version()).isEqualTo(1);
        assertThat(mutation.movement())
                .extracting(
                        InventoryMovement::id,
                        InventoryMovement::type,
                        InventoryMovement::quantityDelta,
                        InventoryMovement::reason,
                        InventoryMovement::reference,
                        InventoryMovement::occurredAt)
                .containsExactly(MOVEMENT_ID, InventoryMovementType.RECEIPT, 12L, REASON, REFERENCE, NOW);
    }

    @Test
    void reconciliationCreatesDirectionalMovementAndPreservesReservations() {
        var original =
                InventoryBalance.restore(WAREHOUSE_ID, VARIANT_ID, new StockQuantity(10), new StockQuantity(3), 7);

        var increase = original.reconcile(MOVEMENT_ID, new StockQuantity(15), REASON, null, NOW);
        var decrease = original.reconcile(MOVEMENT_ID, new StockQuantity(5), REASON, null, NOW);

        assertThat(increase.balance().onHand()).isEqualTo(new StockQuantity(15));
        assertThat(increase.balance().reserved()).isEqualTo(new StockQuantity(3));
        assertThat(increase.movement().type()).isEqualTo(InventoryMovementType.ADJUSTMENT_INCREASE);
        assertThat(increase.movement().quantityDelta()).isEqualTo(5);
        assertThat(decrease.balance().available()).isEqualTo(new StockQuantity(2));
        assertThat(decrease.movement().type()).isEqualTo(InventoryMovementType.ADJUSTMENT_DECREASE);
        assertThat(decrease.movement().quantityDelta()).isEqualTo(-5);
    }

    @Test
    void reserveReleaseAndCommitPreservePhysicalSemantics() {
        var received = InventoryBalance.empty(WAREHOUSE_ID, VARIANT_ID)
                .receive(MOVEMENT_ID, new StockQuantity(10), REASON, null, NOW)
                .balance();
        var reserved = received.reserve(new StockQuantity(4));
        var released = reserved.release(new StockQuantity(1));

        var committed = released.commit(
                new MovementId(new UUID(0, 4)),
                new StockQuantity(3),
                new MovementReason("Committed order reservation"),
                new MovementReference("ORDER-17"),
                NOW.plusSeconds(60));

        assertThat(reserved.onHand()).isEqualTo(new StockQuantity(10));
        assertThat(reserved.reserved()).isEqualTo(new StockQuantity(4));
        assertThat(reserved.available()).isEqualTo(new StockQuantity(6));
        assertThat(released.reserved()).isEqualTo(new StockQuantity(3));
        assertThat(committed.balance().onHand()).isEqualTo(new StockQuantity(7));
        assertThat(committed.balance().reserved()).isEqualTo(StockQuantity.ZERO);
        assertThat(committed.movement().type()).isEqualTo(InventoryMovementType.RESERVATION_COMMIT);
        assertThat(committed.movement().quantityDelta()).isEqualTo(-3);
    }

    @Test
    void rejectsOversellReservedUnderflowAndReconciliationBelowReserved() {
        var balance = InventoryBalance.restore(WAREHOUSE_ID, VARIANT_ID, new StockQuantity(5), new StockQuantity(3), 0);

        assertInvariant(() -> balance.reserve(new StockQuantity(3)), InventoryInvariant.INSUFFICIENT_STOCK);
        assertInvariant(() -> balance.release(new StockQuantity(4)), InventoryInvariant.RESERVED_UNDERFLOW);
        assertInvariant(
                () -> balance.commit(MOVEMENT_ID, new StockQuantity(4), REASON, null, NOW),
                InventoryInvariant.RESERVED_UNDERFLOW);
        assertInvariant(
                () -> balance.reconcile(MOVEMENT_ID, new StockQuantity(2), REASON, null, NOW),
                InventoryInvariant.RESERVED_EXCEEDS_ON_HAND);
    }

    @Test
    void rejectsZeroCommandsNoopAdjustmentAndArithmeticOverflow() {
        var balance = InventoryBalance.restore(WAREHOUSE_ID, VARIANT_ID, new StockQuantity(5), StockQuantity.ZERO, 0);

        assertInvariant(
                () -> balance.receive(MOVEMENT_ID, StockQuantity.ZERO, REASON, null, NOW),
                InventoryInvariant.NON_POSITIVE_QUANTITY);
        assertInvariant(
                () -> balance.reconcile(MOVEMENT_ID, new StockQuantity(5), REASON, null, NOW),
                InventoryInvariant.NO_PHYSICAL_CHANGE);
        assertInvariant(
                () -> InventoryBalance.restore(
                                WAREHOUSE_ID, VARIANT_ID, new StockQuantity(Long.MAX_VALUE), StockQuantity.ZERO, 0)
                        .receive(MOVEMENT_ID, new StockQuantity(1), REASON, null, NOW),
                InventoryInvariant.QUANTITY_OVERFLOW);
        assertInvariant(
                () -> InventoryBalance.restore(
                                WAREHOUSE_ID, VARIANT_ID, new StockQuantity(1), StockQuantity.ZERO, Long.MAX_VALUE)
                        .reserve(new StockQuantity(1)),
                InventoryInvariant.VERSION_OVERFLOW);
    }

    @Test
    void restoreRejectsImpossibleBalanceAndVersion() {
        assertInvariant(
                () -> InventoryBalance.restore(WAREHOUSE_ID, VARIANT_ID, new StockQuantity(1), new StockQuantity(2), 0),
                InventoryInvariant.RESERVED_EXCEEDS_ON_HAND);
        assertInvariant(
                () -> InventoryBalance.restore(WAREHOUSE_ID, VARIANT_ID, StockQuantity.ZERO, StockQuantity.ZERO, -1),
                InventoryInvariant.INVALID_VERSION);
    }

    private static void assertInvariant(Runnable operation, InventoryInvariant expected) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        InventoryInvariantViolation.class,
                        violation -> assertThat(violation.invariant()).isEqualTo(expected));
    }
}
