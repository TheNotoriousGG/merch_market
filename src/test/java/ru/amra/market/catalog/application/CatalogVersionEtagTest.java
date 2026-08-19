package ru.amra.market.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CatalogVersionEtagTest {

    @Test
    void roundTripsStrongVersionTags() {
        assertThat(CatalogVersionEtag.format(42)).isEqualTo("\"v42\"");
        assertThat(CatalogVersionEtag.parse("\"v42\"")).isEqualTo(42);
    }

    @Test
    void rejectsWeakMalformedNegativeAndOverflowingTags() {
        assertThatThrownBy(() -> CatalogVersionEtag.parse("W/\"v1\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogVersionEtag.parse("\"other\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogVersionEtag.parse("\"v999999999999999999999999\""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogVersionEtag.format(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
