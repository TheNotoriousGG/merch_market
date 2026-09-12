package ru.amra.market.pricing;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PricingEngineTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");
    private final PricingEngine engine = new PricingEngine();

    @Test
    void periodUsesInclusiveStartAndExclusiveEnd() {
        var variant = UUID.randomUUID();
        var input = new PricingEngine.LineInput(UUID.randomUUID(), variant, 1, 101);
        var promotion = new PricingEngine.PercentPromotion(UUID.randomUUID(), "Half", START, END, List.of(variant), 50);

        assertThat(engine.quote(List.of(input), List.of(promotion), START)
                        .lines()
                        .getFirst()
                        .discountMinor())
                .isEqualTo(51);
        assertThat(engine.quote(List.of(input), List.of(promotion), END)
                        .lines()
                        .getFirst()
                        .discountMinor())
                .isZero();
    }

    @Test
    void equalOffersResolveByStablePromotionId() {
        var variant = UUID.randomUUID();
        var smaller = new UUID(0, 1);
        var larger = new UUID(0, 2);
        var input = new PricingEngine.LineInput(UUID.randomUUID(), variant, 1, 1000);
        var result = engine.quote(
                List.of(input),
                List.of(
                        new PricingEngine.FixedLinePromotion(larger, "B", START, END, List.of(variant), 100),
                        new PricingEngine.FixedLinePromotion(smaller, "A", START, END, List.of(variant), 100)),
                START);

        assertThat(requireNonNull(result.lines().getFirst().promotion()).promotionId())
                .isEqualTo(smaller);
    }

    @Test
    void multiBuyDiscountsOnlyCompleteBundles() {
        var variant = UUID.randomUUID();
        var promotion = new PricingEngine.MultiBuyPromotion(
                UUID.randomUUID(), "Two plus one", START, END, List.of(variant), 2, 3);

        var two = engine.quote(
                List.of(new PricingEngine.LineInput(UUID.randomUUID(), variant, 2, 100)), List.of(promotion), START);
        var three = engine.quote(
                List.of(new PricingEngine.LineInput(UUID.randomUUID(), variant, 3, 100)), List.of(promotion), START);

        assertThat(two.totalMinor()).isEqualTo(200);
        assertThat(three.totalMinor()).isEqualTo(200);
    }

    @Test
    void fixedDiscountCannotMakeLineNegative() {
        var variant = UUID.randomUUID();
        var promotion =
                new PricingEngine.FixedLinePromotion(UUID.randomUUID(), "Large", START, END, List.of(variant), 1000);
        var result = engine.quote(
                List.of(new PricingEngine.LineInput(UUID.randomUUID(), variant, 1, 100)), List.of(promotion), START);

        assertThat(result.totalMinor()).isZero();
    }

    @Test
    void rejectsInvalidInputsAndAllocation() {
        var variant = UUID.randomUUID();
        assertThatThrownBy(() -> engine.quote(
                        List.of(new PricingEngine.LineInput(UUID.randomUUID(), variant, 0, 100)), List.of(), START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.quote(
                        List.of(new PricingEngine.LineInput(UUID.randomUUID(), variant, 1, 0)), List.of(), START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PricingEngine.allocate(-1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PricingEngine.allocate(0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PricingEngine.allocate(2, List.of(new PricingEngine.AllocationInput(UUID.randomUUID(), 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        PricingEngine.allocate(0, List.of(new PricingEngine.AllocationInput(UUID.randomUUID(), 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesPromotionDefinitions() {
        var variant = UUID.randomUUID();
        assertThatThrownBy(() ->
                        new PricingEngine.PercentPromotion(UUID.randomUUID(), "Bad", START, END, List.of(variant), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PricingEngine.PercentPromotion(UUID.randomUUID(), "Bad", START, END, List.of(variant), 91))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new PricingEngine.FixedLinePromotion(UUID.randomUUID(), "Bad", END, START, List.of(variant), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingEngine.MultiBuyPromotion(
                        UUID.randomUUID(), "Bad", START, END, List.of(variant), 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
