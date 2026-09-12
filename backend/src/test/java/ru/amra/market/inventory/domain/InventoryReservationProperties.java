package ru.amra.market.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class InventoryReservationProperties {

    private static final Instant NOW = Instant.parse("2026-08-19T10:00:00Z");
    private static final ReservationId RESERVATION_ID = new ReservationId(new UUID(0, 1));
    private static final ReservationEventId EVENT_ID = new ReservationEventId(new UUID(0, 2));
    private static final ReservationOwnerReference OWNER = new ReservationOwnerReference(new UUID(0, 3));
    private static final WarehouseId WAREHOUSE = new WarehouseId(new UUID(0, 4));
    private static final CatalogVariantId VARIANT = new CatalogVariantId(new UUID(0, 5));

    @Property
    void duplicateAggregationPreservesTotal(
            @ForAll @LongRange(min = 1, max = 1_000_000) long first,
            @ForAll @LongRange(min = 1, max = 1_000_000) long second) {
        var lines = ReservationLines.of(List.of(line(first), line(second)));

        assertThat(lines.size()).isOne();
        assertThat(lines.values().getFirst().quantity().value()).isEqualTo(first + second);
    }

    @Property
    void oneExtensionAdvancesExpiryByExactTtl(@ForAll @IntRange(min = 1, max = 1_440) int ttlMinutes) {
        var reservation = create(Duration.ofMinutes(ttlMinutes));
        var previousExpiry = reservation.expiresAt();

        var extended = reservation.extend(EVENT_ID, NOW.plusSeconds(1), Duration.ofMinutes(ttlMinutes));

        assertThat(extended.reservation().expiresAt()).isEqualTo(previousExpiry.plus(Duration.ofMinutes(ttlMinutes)));
        assertThat(extended.reservation().status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(extended.reservation().extensionCount()).isOne();
    }

    private static InventoryReservation create(Duration ttl) {
        return InventoryReservation.create(
                        RESERVATION_ID, EVENT_ID, OWNER, ReservationLines.of(List.of(line(1))), NOW, ttl)
                .reservation();
    }

    private static ReservationLine line(long quantity) {
        return new ReservationLine(WAREHOUSE, VARIANT, new StockQuantity(quantity));
    }
}
