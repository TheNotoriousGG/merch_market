package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductMerchandisingTest {

    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    @Test
    void evaluatesOpenAndBoundedNewArrivalWindows() {
        assertThat(new ProductMerchandising(true, null, false, null, false).isNewAt(NOW))
                .isTrue();
        assertThat(new ProductMerchandising(true, NOW.plusSeconds(1), false, null, false).isNewAt(NOW))
                .isTrue();
        assertThat(new ProductMerchandising(true, NOW, false, null, false).isNewAt(NOW))
                .isFalse();
        assertThat(ProductMerchandising.none().isNewAt(NOW)).isFalse();
    }

    @Test
    void validatesNewArrivalAndSaleCombinations() {
        assertThatThrownBy(() -> new ProductMerchandising(false, NOW, false, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductMerchandising(false, null, true, null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductMerchandising(false, null, true, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductMerchandising(false, null, true, 91, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductMerchandising(false, null, false, 20, false))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(new ProductMerchandising(false, null, true, 20, true).salePercent())
                .isEqualTo(20);
    }
}
