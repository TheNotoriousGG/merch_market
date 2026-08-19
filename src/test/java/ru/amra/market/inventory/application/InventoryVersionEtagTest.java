package ru.amra.market.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InventoryVersionEtagTest {

    @Test
    void roundTripsStrongVersion() {
        assertThat(InventoryVersionEtag.parse(InventoryVersionEtag.format(42))).isEqualTo(42);
    }

    @Test
    void rejectsWeakMalformedOverflowingAndNegativeVersions() {
        assertThatThrownBy(() -> InventoryVersionEtag.parse("W/\"v1\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryVersionEtag.parse("\"1\"")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryVersionEtag.parse("\"v999999999999999999999\""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryVersionEtag.format(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
