package ru.amra.market.catalog.infrastructure.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("amra.catalog.media.storage")
record MediaStorageProperties(
        String endpoint, String accessKey, String secretKey, String bucket, Duration uploadTtl, long maxSize) {}
