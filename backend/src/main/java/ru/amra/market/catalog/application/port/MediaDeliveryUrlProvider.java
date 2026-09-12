package ru.amra.market.catalog.application.port;

import java.net.URI;
import java.util.UUID;

/** Resolves opaque public media identities without exposing storage object keys. */
public interface MediaDeliveryUrlProvider {

    /** Returns the externally consumable delivery URL for one media resource. */
    URI publicUrl(UUID mediaId, String objectKey);
}
