package ru.amra.market.catalog.application.port;

import java.util.UUID;

/** Source of time-ordered catalog identities. */
public interface CatalogIdGenerator {

    /** Returns a new UUIDv7. */
    UUID next();
}
