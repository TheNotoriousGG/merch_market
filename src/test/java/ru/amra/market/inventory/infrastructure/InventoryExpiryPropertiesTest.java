package ru.amra.market.inventory.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InventoryExpiryPropertiesTest {

    @Test
    void rejectsUnboundedOrUnsafeSchedulingValues() {
        assertThatThrownBy(() ->
                        new InventoryExpiryProperties(true, Duration.ZERO, Duration.ofSeconds(30), 100, "instance"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryExpiryProperties(
                        true, Duration.ofSeconds(5), Duration.ofSeconds(5), 100, "instance"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InventoryExpiryProperties(
                        true, Duration.ofSeconds(5), Duration.ofSeconds(30), 1001, "instance"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new InventoryExpiryProperties(true, Duration.ofSeconds(5), Duration.ofSeconds(30), 100, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
