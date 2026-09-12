package ru.amra.market.customer;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded periodic cleanup for expired guest identities and inactive carts. */
@Component
final class CustomerRetentionScheduler {
    private final CustomerShoppingService shopping;

    CustomerRetentionScheduler(CustomerShoppingService shopping) {
        this.shopping = shopping;
    }

    @Scheduled(
            fixedDelayString = "${amra.customer.retention.fixed-delay:PT1H}",
            initialDelayString = "${amra.customer.retention.initial-delay:PT5M}")
    void cleanup() {
        shopping.cleanupExpired(200);
    }
}
