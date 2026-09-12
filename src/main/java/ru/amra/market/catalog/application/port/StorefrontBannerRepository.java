package ru.amra.market.catalog.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.amra.market.catalog.domain.StorefrontBanner;

/** Persistence boundary for editorial storefront banners. */
public interface StorefrontBannerRepository {
    List<StorefrontBanner> findAll();

    List<StorefrontBanner> findPublishedAt(Instant now);

    Optional<StorefrontBanner> findById(UUID id);

    StorefrontBanner save(StorefrontBanner banner);
}
