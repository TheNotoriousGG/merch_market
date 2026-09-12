package ru.amra.market.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryReservationTest {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");
    private static final ReservationId RESERVATION_ID = new ReservationId(new UUID(0, 1));
    private static final ReservationEventId EVENT_ID = new ReservationEventId(new UUID(0, 2));
    private static final ReservationOwnerReference OWNER = new ReservationOwnerReference(new UUID(0, 3));
    private static final WarehouseId WAREHOUSE = new WarehouseId(new UUID(0, 4));
    private static final CatalogVariantId VARIANT = new CatalogVariantId(new UUID(0, 5));

    @Test
    void createsActiveReservationWithDefaultTtlAndEvent() {
        var result = create();

        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(result.reservation().expiresAt()).isEqualTo(NOW.plus(InventoryReservation.DEFAULT_TTL));
        assertThat(result.reservation().extensionCount()).isZero();
        assertThat(result.reservation().version()).isZero();
        assertThat(result.event().type()).isEqualTo(ReservationEventType.CREATED);
        assertThat(result.event().previousStatus()).isNull();
    }

    @Test
    void aggregatesDuplicateLinesInFirstSeenOrder() {
        var secondVariant = new CatalogVariantId(new UUID(0, 6));

        var lines = ReservationLines.of(List.of(line(VARIANT, 2), line(secondVariant, 4), line(VARIANT, 3)));

        assertThat(lines.values()).containsExactly(line(VARIANT, 5), line(secondVariant, 4));
    }

    @Test
    void rejectsEmptyNullAndExcessiveLines() {
        assertViolation(() -> ReservationLines.of(List.of()), InventoryInvariant.INVALID_RESERVATION_LINES);
        assertViolation(() -> ReservationLines.of(null), InventoryInvariant.INVALID_RESERVATION_LINES);

        var excessive = new ArrayList<ReservationLine>();
        for (int index = 0; index <= ReservationLines.MAX_DISTINCT_LINES; index++) {
            excessive.add(line(new CatalogVariantId(new UUID(1, index + 1L)), 1));
        }
        assertViolation(() -> ReservationLines.of(excessive), InventoryInvariant.RESERVATION_LINE_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsDuplicateQuantityOverflow() {
        assertViolation(
                () -> ReservationLines.of(List.of(line(VARIANT, Long.MAX_VALUE), line(VARIANT, 1))),
                InventoryInvariant.QUANTITY_OVERFLOW);
    }

    @Test
    void extendsExactlyOnceFromPreviousExpiry() {
        var original = create().reservation();

        var result = original.extend(EVENT_ID, NOW.plusSeconds(1), Duration.ofMinutes(10));

        assertThat(result.reservation().expiresAt())
                .isEqualTo(original.expiresAt().plus(Duration.ofMinutes(10)));
        assertThat(result.reservation().extensionCount()).isOne();
        assertThat(result.reservation().version()).isOne();
        assertThat(result.event().type()).isEqualTo(ReservationEventType.EXTENDED);
        assertThat(original.extensionCount()).isZero();

        assertViolation(
                () -> result.reservation().extend(EVENT_ID, NOW.plusSeconds(2), Duration.ofMinutes(10)),
                InventoryInvariant.RESERVATION_EXTENSION_LIMIT_REACHED);
    }

    @Test
    void commitsOnlyBeforeDeadlineAndTerminalStateCannotTransition() {
        var original = create().reservation();
        var committed = original.commit(EVENT_ID, original.expiresAt().minusNanos(1));

        assertThat(committed.reservation().status()).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(committed.reservation().version()).isOne();
        assertThat(committed.event().type()).isEqualTo(ReservationEventType.COMMITTED);
        assertViolation(
                () -> committed.reservation().release(EVENT_ID, NOW), InventoryInvariant.RESERVATION_STATE_CONFLICT);
        assertViolation(() -> original.commit(EVENT_ID, original.expiresAt()), InventoryInvariant.RESERVATION_EXPIRED);
    }

    @Test
    void releaseBeforeDeadlineBecomesTerminal() {
        var result = create().reservation().release(EVENT_ID, NOW.plusSeconds(1));

        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(result.event().type()).isEqualTo(ReservationEventType.RELEASED);
        assertViolation(
                () -> result.reservation().release(EVENT_ID, NOW.plusSeconds(2)),
                InventoryInvariant.RESERVATION_STATE_CONFLICT);
    }

    @Test
    void expiresAtDeadlineButNeverBeforeIt() {
        var original = create().reservation();

        assertViolation(
                () -> original.expire(EVENT_ID, original.expiresAt().minusNanos(1)),
                InventoryInvariant.RESERVATION_NOT_EXPIRED);

        var result = original.expire(EVENT_ID, original.expiresAt());
        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(result.event().type()).isEqualTo(ReservationEventType.EXPIRED);
    }

    @Test
    void rejectsInvalidTtlVersionAndExtensionCount() {
        var lines = lines();
        assertViolation(
                () -> InventoryReservation.create(RESERVATION_ID, EVENT_ID, OWNER, lines, NOW, Duration.ZERO),
                InventoryInvariant.INVALID_RESERVATION);
        assertViolation(
                () -> InventoryReservation.restore(RESERVATION_ID, OWNER, lines, ReservationStatus.ACTIVE, NOW, 2, 0),
                InventoryInvariant.INVALID_RESERVATION);
        assertViolation(
                () -> InventoryReservation.restore(RESERVATION_ID, OWNER, lines, ReservationStatus.ACTIVE, NOW, 0, -1),
                InventoryInvariant.INVALID_RESERVATION);
    }

    private static ReservationTransition create() {
        return InventoryReservation.create(
                RESERVATION_ID, EVENT_ID, OWNER, lines(), NOW, InventoryReservation.DEFAULT_TTL);
    }

    private static ReservationLines lines() {
        return ReservationLines.of(List.of(line(VARIANT, 2)));
    }

    private static ReservationLine line(CatalogVariantId variantId, long quantity) {
        return new ReservationLine(WAREHOUSE, variantId, new StockQuantity(quantity));
    }

    private static void assertViolation(Runnable action, InventoryInvariant invariant) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        InventoryInvariantViolation.class,
                        exception -> assertThat(exception.invariant()).isEqualTo(invariant));
    }
}
