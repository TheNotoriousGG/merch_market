package ru.amra.market.pricing;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves effective SKU prices and promotions from PostgreSQL and returns immutable quotes. */
@Service
public final class PricingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final PricingEngine engine = new PricingEngine();

    public PricingService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Quotes one SKU at the injected current instant. */
    public PricingEngine.LineResult quote(UUID lineId, UUID variantId, int quantity) {
        var at = clock.instant();
        var prices = jdbc.query(
                """
                select amount_minor from pricing_base_price_periods
                where variant_id = :variant and starts_at <= :at and (ends_at is null or ends_at > :at)
                order by starts_at desc, id limit 2
                """,
                Map.of("variant", variantId, "at", Timestamp.from(at)),
                (result, row) -> result.getLong("amount_minor"));
        if (prices.size() > 1) {
            throw new PriceUnavailableException(variantId, "overlapping");
        }
        var amount = prices.isEmpty() ? legacyPrice(variantId) : prices.getFirst();
        var input = new PricingEngine.LineInput(lineId, variantId, quantity, amount);
        return engine.quote(List.of(input), promotions(variantId, at), at)
                .lines()
                .getFirst();
    }

    private long legacyPrice(UUID variantId) {
        var prices = jdbc.query("""
                select product.price_minor from catalog_product_variants variant
                join catalog_products product on product.id = variant.product_id
                where variant.id = :variant and product.price_minor is not null
                """, Map.of("variant", variantId), (result, row) -> result.getLong("price_minor"));
        if (prices.size() != 1) {
            throw new PriceUnavailableException(variantId, "missing");
        }
        return prices.getFirst();
    }

    private List<PricingEngine.Promotion> promotions(UUID variantId, Instant at) {
        return jdbc.query("""
                select promotion.id, promotion.name, promotion.promotion_type, promotion.percent,
                       promotion.amount_minor, promotion.paid_quantity, promotion.bundle_quantity,
                       promotion.starts_at, promotion.ends_at
                from pricing_promotions promotion
                join pricing_promotion_variants target on target.promotion_id = promotion.id
                where target.variant_id = :variant and promotion.enabled
                  and promotion.starts_at <= :at and promotion.ends_at > :at
                order by promotion.id
                """, Map.of("variant", variantId, "at", Timestamp.from(at)), (result, row) -> {
            var id = result.getObject("id", UUID.class);
            var name = result.getString("name");
            var starts = result.getTimestamp("starts_at").toInstant();
            var ends = result.getTimestamp("ends_at").toInstant();
            var targets = List.of(variantId);
            return switch (result.getString("promotion_type")) {
                case "PERCENT" ->
                    new PricingEngine.PercentPromotion(id, name, starts, ends, targets, result.getInt("percent"));
                case "FIXED_LINE" ->
                    new PricingEngine.FixedLinePromotion(
                            id, name, starts, ends, targets, result.getLong("amount_minor"));
                case "MULTI_BUY" ->
                    new PricingEngine.MultiBuyPromotion(
                            id,
                            name,
                            starts,
                            ends,
                            targets,
                            result.getInt("paid_quantity"),
                            result.getInt("bundle_quantity"));
                default -> throw new IllegalStateException("Unsupported promotion type");
            };
        });
    }

    /** Signals that an SKU has no unambiguous effective base price. */
    public static final class PriceUnavailableException extends RuntimeException {
        PriceUnavailableException(UUID variantId, String reason) {
            super("Price for variant " + variantId + " is " + reason);
        }
    }
}
