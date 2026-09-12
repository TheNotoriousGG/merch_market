package ru.amra.market.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class PricingEngineProperties {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2027-01-01T00:00:00Z");

    @Property
    void quoteIsReproducibleAndNeverNegative(
            @ForAll @LongRange(min = 1, max = 10_000_000) long price,
            @ForAll @IntRange(min = 1, max = 99) int quantity,
            @ForAll @IntRange(min = 1, max = 90) int percent) {
        var variant = UUID.randomUUID();
        var line = new PricingEngine.LineInput(UUID.randomUUID(), variant, quantity, price);
        var promotion =
                new PricingEngine.PercentPromotion(UUID.randomUUID(), "Скидка", START, END, List.of(variant), percent);
        var engine = new PricingEngine();
        var first = engine.quote(List.of(line), List.of(promotion), START.plusSeconds(1));
        var second = engine.quote(List.of(line), List.of(promotion), START.plusSeconds(1));

        assertThat(first).isEqualTo(second);
        assertThat(first.totalMinor()).isBetween(0L, Math.multiplyExact(price, quantity));
    }

    @Property
    void allocationPreservesExactDiscount(
            @ForAll @LongRange(min = 1, max = 1_000_000) long first,
            @ForAll @LongRange(min = 1, max = 1_000_000) long second,
            @ForAll @IntRange(min = 0, max = 100) int percentage) {
        var total = first + second;
        var discount = total * percentage / 100;
        var allocations = PricingEngine.allocate(
                discount,
                List.of(
                        new PricingEngine.AllocationInput(new UUID(0, 1), first),
                        new PricingEngine.AllocationInput(new UUID(0, 2), second)));
        assertThat(allocations.stream()
                        .mapToLong(PricingEngine.Allocation::discountMinor)
                        .sum())
                .isEqualTo(discount);
    }
}
