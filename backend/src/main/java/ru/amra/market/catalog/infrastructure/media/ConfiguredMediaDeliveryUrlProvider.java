package ru.amra.market.catalog.infrastructure.media;

import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.amra.market.catalog.application.port.MediaDeliveryUrlProvider;

/** Configuration-backed media URL adapter used until a production CDN provider is selected. */
@Component
class ConfiguredMediaDeliveryUrlProvider implements MediaDeliveryUrlProvider {

    private final URI baseUrl;

    ConfiguredMediaDeliveryUrlProvider(@Value("${amra.catalog.media.public-base-url}") String baseUrl) {
        var normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + '/';
        this.baseUrl = URI.create(normalized);
        if (!this.baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("Catalog public media base URL must be absolute");
        }
    }

    @Override
    public URI publicUrl(UUID mediaId, String objectKey) {
        return baseUrl.resolve(objectKey);
    }
}
