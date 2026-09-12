package ru.amra.market.inventory.application.port;

import java.time.Duration;

/** PostgreSQL-time coordination boundary for one bounded background job. */
public interface InventoryJobLease {
    /** Acquires, renews or takes over an expired lease atomically. */
    boolean tryAcquire(String jobName, String ownerInstanceId, Duration leaseDuration);
}
