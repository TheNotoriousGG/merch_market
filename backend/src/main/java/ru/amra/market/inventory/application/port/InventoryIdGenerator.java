package ru.amra.market.inventory.application.port;

import java.util.UUID;

/** Source of time-ordered inventory identities. */
public interface InventoryIdGenerator {

    /** Returns a new UUIDv7. */
    UUID next();
}
