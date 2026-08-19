package ru.amra.market.inventory.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated bounds for the reservation expiry worker. */
@ConfigurationProperties("amra.inventory.expiry")
public record InventoryExpiryProperties(
        boolean enabled, Duration scanInterval, Duration leaseDuration, int batchSize, String instanceId) {

    public InventoryExpiryProperties {
        if (scanInterval == null || scanInterval.isNegative() || scanInterval.isZero()) {
            throw new IllegalArgumentException("Inventory expiry scan interval must be positive");
        }
        if (leaseDuration == null || leaseDuration.compareTo(scanInterval) <= 0) {
            throw new IllegalArgumentException("Inventory expiry lease must exceed the scan interval");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Inventory expiry batch size must be between 1 and 1000");
        }
        if (instanceId == null || instanceId.isBlank() || instanceId.length() > 128) {
            throw new IllegalArgumentException("Inventory expiry instance id must contain 1-128 characters");
        }
    }
}
