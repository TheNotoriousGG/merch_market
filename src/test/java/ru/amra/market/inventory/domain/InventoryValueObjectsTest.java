package ru.amra.market.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryValueObjectsTest {

    @Test
    void reasonAndReferenceAreTrimmedAndBounded() {
        assertThat(new MovementReason("  Physical count  ").value()).isEqualTo("Physical count");
        assertThat(new MovementReference("  DOC-42  ").value()).isEqualTo("DOC-42");

        assertInvariant(() -> new MovementReason("  x "), InventoryInvariant.INVALID_REASON);
        assertInvariant(() -> new MovementReference("  "), InventoryInvariant.INVALID_REFERENCE);
    }

    @Test
    void movementTypeEnforcesItsDeltaDirection() {
        var movementId = new MovementId(new UUID(0, 1));
        var warehouseId = new WarehouseId(new UUID(0, 2));
        var variantId = new CatalogVariantId(new UUID(0, 3));
        var reason = new MovementReason("Warehouse receipt");

        assertInvariant(
                () -> new InventoryMovement(
                        movementId,
                        warehouseId,
                        variantId,
                        InventoryMovementType.RECEIPT,
                        -1,
                        reason,
                        null,
                        Instant.EPOCH),
                InventoryInvariant.INVALID_MOVEMENT);
        assertInvariant(
                () -> new InventoryMovement(
                        movementId,
                        warehouseId,
                        variantId,
                        InventoryMovementType.ADJUSTMENT_DECREASE,
                        0,
                        reason,
                        null,
                        Instant.EPOCH),
                InventoryInvariant.INVALID_MOVEMENT);
    }

    @Test
    void quantityRejectsNegativeAndCheckedSubtractionUnderflow() {
        assertInvariant(() -> new StockQuantity(-1), InventoryInvariant.INVALID_QUANTITY);
        assertInvariant(
                () -> new StockQuantity(1).minus(new StockQuantity(2), InventoryInvariant.INSUFFICIENT_STOCK),
                InventoryInvariant.INSUFFICIENT_STOCK);
    }

    private static void assertInvariant(Runnable operation, InventoryInvariant expected) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        InventoryInvariantViolation.class,
                        violation -> assertThat(violation.invariant()).isEqualTo(expected));
    }
}
