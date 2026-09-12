package ru.amra.market.catalog.application;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.ProductId;

/** Creates bounded direct-to-object-storage upload reservations. */
public interface MediaUploadService {

    Upload prepare(ProductId productId, String fileName, String contentType, long size);

    void verify(ProductId productId, String objectKey, String contentType);

    void delete(Iterable<String> objectKeys);

    Upload prepareBanner(UUID bannerId, String fileName, String contentType, long size);

    void verifyBanner(@Nullable UUID bannerId, String objectKey);

    record Upload(String objectKey, URI uploadUrl, Instant expiresAt, String contentType, long maxSize) {}
}
