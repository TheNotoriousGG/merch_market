package ru.amra.market.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Deterministic RUB, tax-inclusive calculator with bounded non-stacking promotions. */
public final class PricingEngine {

    /** Calculates immutable line results and chooses the single best eligible promotion per line. */
    public Quote quote(List<LineInput> inputs, List<Promotion> promotions, Instant at) {
        var lines = inputs.stream().map(input -> line(input, promotions, at)).toList();
        return new Quote(lines, lines.stream().mapToLong(LineResult::totalMinor).sum(), "RUB", at);
    }

    /** Allocates an order-level discount proportionally and assigns rounding residue by stable line id. */
    public static List<Allocation> allocate(long discountMinor, List<AllocationInput> lines) {
        if (discountMinor < 0 || lines.isEmpty()) {
            throw new IllegalArgumentException("discount and lines must be positive");
        }
        var total = lines.stream().mapToLong(AllocationInput::amountMinor).sum();
        if (discountMinor > total || total == 0) {
            throw new IllegalArgumentException("discount exceeds allocatable amount");
        }
        var result = new ArrayList<Allocation>();
        long allocated = 0;
        for (var line : lines) {
            var share = BigDecimal.valueOf(discountMinor)
                    .multiply(BigDecimal.valueOf(line.amountMinor()))
                    .divide(BigDecimal.valueOf(total), 0, RoundingMode.DOWN)
                    .longValueExact();
            result.add(new Allocation(line.lineId(), share));
            allocated += share;
        }
        var residue = discountMinor - allocated;
        result.sort(Comparator.comparing(Allocation::lineId));
        for (var index = 0; index < residue; index++) {
            var current = result.get(index % result.size());
            result.set(index % result.size(), new Allocation(current.lineId(), current.discountMinor() + 1));
        }
        return List.copyOf(result);
    }

    private static LineResult line(LineInput input, List<Promotion> promotions, Instant at) {
        if (input.quantity() < 1 || input.unitPriceMinor() < 1) {
            throw new IllegalArgumentException("quantity and price must be positive");
        }
        var base = Math.multiplyExact(input.unitPriceMinor(), input.quantity());
        var best = promotions.stream()
                .filter(promotion -> promotion.applies(input, at))
                .map(promotion ->
                        new AppliedPromotion(promotion.id(), promotion.name(), promotion.discount(input, base)))
                .max(Comparator.comparingLong(AppliedPromotion::discountMinor)
                        .thenComparing(AppliedPromotion::promotionId, Comparator.reverseOrder()))
                .orElse(null);
        var discount = best == null ? 0 : Math.min(base, best.discountMinor());
        return new LineResult(
                input.lineId(),
                input.variantId(),
                input.quantity(),
                input.unitPriceMinor(),
                base,
                discount,
                base - discount,
                best);
    }

    public record LineInput(UUID lineId, UUID variantId, int quantity, long unitPriceMinor) {}

    public record Quote(List<LineResult> lines, long totalMinor, String currency, Instant calculatedAt) {
        public Quote {
            lines = List.copyOf(lines);
        }
    }

    public record LineResult(
            UUID lineId,
            UUID variantId,
            int quantity,
            long unitPriceMinor,
            long baseMinor,
            long discountMinor,
            long totalMinor,
            @Nullable AppliedPromotion promotion) {}

    public record AppliedPromotion(UUID promotionId, String name, long discountMinor) {}

    public record AllocationInput(UUID lineId, long amountMinor) {}

    public record Allocation(UUID lineId, long discountMinor) {}

    /** Closed promotion vocabulary; new behavior requires a code change and tests. */
    public sealed interface Promotion permits PercentPromotion, FixedLinePromotion, MultiBuyPromotion {
        UUID id();

        String name();

        Instant startsAt();

        Instant endsAt();

        boolean targets(UUID variantId);

        long discount(LineInput input, long baseMinor);

        default boolean applies(LineInput input, Instant at) {
            return !at.isBefore(startsAt()) && at.isBefore(endsAt()) && targets(input.variantId());
        }
    }

    public record PercentPromotion(
            UUID id, String name, Instant startsAt, Instant endsAt, List<UUID> variantIds, int percent)
            implements Promotion {
        public PercentPromotion {
            variantIds = List.copyOf(variantIds);
            if (percent < 1 || percent > 90 || !endsAt.isAfter(startsAt)) throw new IllegalArgumentException();
        }

        @Override
        public boolean targets(UUID variantId) {
            return variantIds.contains(variantId);
        }

        @Override
        public long discount(LineInput input, long baseMinor) {
            return BigDecimal.valueOf(baseMinor)
                    .multiply(BigDecimal.valueOf(percent))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
    }

    public record FixedLinePromotion(
            UUID id, String name, Instant startsAt, Instant endsAt, List<UUID> variantIds, long amountMinor)
            implements Promotion {
        public FixedLinePromotion {
            variantIds = List.copyOf(variantIds);
            if (amountMinor < 1 || !endsAt.isAfter(startsAt)) throw new IllegalArgumentException();
        }

        @Override
        public boolean targets(UUID variantId) {
            return variantIds.contains(variantId);
        }

        @Override
        public long discount(LineInput input, long baseMinor) {
            return Math.min(baseMinor, amountMinor);
        }
    }

    public record MultiBuyPromotion(
            UUID id,
            String name,
            Instant startsAt,
            Instant endsAt,
            List<UUID> variantIds,
            int paidQuantity,
            int bundleQuantity)
            implements Promotion {
        public MultiBuyPromotion {
            variantIds = List.copyOf(variantIds);
            if (paidQuantity < 1 || bundleQuantity <= paidQuantity || !endsAt.isAfter(startsAt)) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public boolean targets(UUID variantId) {
            return variantIds.contains(variantId);
        }

        @Override
        public long discount(LineInput input, long baseMinor) {
            var free = input.quantity() / bundleQuantity * (bundleQuantity - paidQuantity);
            return Math.multiplyExact(input.unitPriceMinor(), free);
        }
    }
}
