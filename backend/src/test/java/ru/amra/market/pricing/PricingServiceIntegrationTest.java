package ru.amra.market.pricing;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@Transactional
class PricingServiceIntegrationTest extends PostgreSqlIntegrationTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PricingService pricing;

    private UUID variantId;

    @BeforeEach
    void createVariant() {
        var categoryId = uuidV7();
        var productId = uuidV7();
        variantId = uuidV7();
        jdbc.update("""
                insert into catalog_categories(id, slug, name, display_order, status)
                values (?, ?, 'Test', 0, 'ACTIVE')
                """, categoryId, "pricing-" + categoryId);
        jdbc.update("""
                insert into catalog_products(
                    id, canonical_slug, name, short_description, description, status,
                    primary_category_id, published_at, price_minor)
                values (?, ?, 'Pricing product', 'Description', 'Description', 'ACTIVE', ?, current_timestamp, 10000)
                """, productId, "pricing-product-" + productId, categoryId);
        jdbc.update(
                """
                insert into catalog_product_variants(
                    id, product_id, sku, label, status, display_order, defining_signature)
                values (?, ?, ?, 'Default', 'ACTIVE', 0, 'default')
                """,
                variantId,
                productId,
                "PRICE-" + productId.toString().substring(0, 8).toUpperCase(Locale.ROOT));
    }

    @Test
    void resolvesSkuPeriodAndChoosesBestNonStackingPromotion() {
        var now = Instant.now();
        insertPrice(12500, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        insertPercentPromotion(10, now);
        insertFixedPromotion(3000, now);

        var quote = pricing.quote(uuidV7(), variantId, 2);

        assertThat(quote.baseMinor()).isEqualTo(25000);
        assertThat(quote.discountMinor()).isEqualTo(3000);
        assertThat(quote.totalMinor()).isEqualTo(22000);
        assertThat(quote.promotion()).isNotNull();
        assertThat(requireNonNull(quote.promotion()).name()).isEqualTo("Fixed");
    }

    @Test
    void databaseRejectsOverlappingEffectivePricePeriods() {
        var now = Instant.now();
        insertPrice(10000, now.minus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS));

        assertThatThrownBy(() -> insertPrice(11000, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)))
                .hasRootCauseInstanceOf(java.sql.SQLException.class);
    }

    private void insertPrice(long amount, Instant starts, Instant ends) {
        jdbc.update("""
                insert into pricing_base_price_periods(id, variant_id, amount_minor, starts_at, ends_at)
                values (?, ?, ?, ?, ?)
                """, uuidV7(), variantId, amount, Timestamp.from(starts), Timestamp.from(ends));
    }

    private void insertPercentPromotion(int percent, Instant now) {
        var id = uuidV7();
        jdbc.update(
                """
                insert into pricing_promotions(
                    id, name, promotion_type, percent, starts_at, ends_at)
                values (?, 'Percent', 'PERCENT', ?, ?, ?)
                """,
                id,
                percent,
                Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
                Timestamp.from(now.plus(1, ChronoUnit.DAYS)));
        jdbc.update("insert into pricing_promotion_variants(promotion_id, variant_id) values (?, ?)", id, variantId);
    }

    private void insertFixedPromotion(long amount, Instant now) {
        var id = uuidV7();
        jdbc.update(
                """
                insert into pricing_promotions(
                    id, name, promotion_type, amount_minor, starts_at, ends_at)
                values (?, 'Fixed', 'FIXED_LINE', ?, ?, ?)
                """,
                id,
                amount,
                Timestamp.from(now.minus(1, ChronoUnit.DAYS)),
                Timestamp.from(now.plus(1, ChronoUnit.DAYS)));
        jdbc.update("insert into pricing_promotion_variants(promotion_id, variant_id) values (?, ?)", id, variantId);
    }

    private UUID uuidV7() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
