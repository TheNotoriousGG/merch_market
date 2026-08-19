package ru.amra.market.inventory.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.amra.market.inventory.application.ExpireInventoryReservations;

/** Thin scheduling adapter; all coordination and mutations remain transactional application work. */
@Component
@ConditionalOnProperty(prefix = "amra.inventory.expiry", name = "enabled", havingValue = "true", matchIfMissing = true)
class InventoryExpiryScheduler {
    private final ExpireInventoryReservations expiry;
    private final InventoryExpiryProperties properties;

    InventoryExpiryScheduler(ExpireInventoryReservations expiry, InventoryExpiryProperties properties) {
        this.expiry = expiry;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${amra.inventory.expiry.scan-interval:5s}",
            fixedDelayString = "${amra.inventory.expiry.scan-interval:5s}")
    void expireDueReservations() {
        expiry.runBatch(properties.instanceId(), properties.leaseDuration(), properties.batchSize());
    }
}
