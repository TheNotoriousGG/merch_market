package ru.amra.market.catalog.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.CatalogIdGenerator;
import ru.amra.market.catalog.application.port.StorefrontBannerRepository;
import ru.amra.market.catalog.domain.StorefrontBanner;

/** Transactional authoring and public query facade for storefront banners. */
@Service
public class ManageStorefrontBanners {
    private final StorefrontBannerRepository repository;
    private final CatalogIdGenerator ids;
    private final CatalogAuditTrail audit;
    private final MediaUploadService media;
    private final Clock clock;

    public ManageStorefrontBanners(
            StorefrontBannerRepository repository,
            CatalogIdGenerator ids,
            CatalogAuditTrail audit,
            MediaUploadService media,
            Clock clock) {
        this.repository = repository;
        this.ids = ids;
        this.audit = audit;
        this.media = media;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StorefrontBanner> listAdmin() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<StorefrontBanner> listPublic() {
        return repository.findPublishedAt(clock.instant());
    }

    @Transactional(readOnly = true)
    public StorefrontBanner get(UUID id) {
        return repository.findById(id).orElseThrow(StorefrontBannerNotFoundException::new);
    }

    @Transactional
    public StorefrontBanner create(SaveCommand command) {
        var now = clock.instant();
        verifyMedia(command, null);
        var saved = repository.save(new StorefrontBanner(
                ids.next(),
                command.internalName(),
                command.eyebrow(),
                command.title(),
                command.description(),
                command.buttonLabel(),
                command.targetType(),
                command.targetValue(),
                command.desktopObjectKey(),
                command.mobileObjectKey(),
                command.status(),
                command.displayOrder(),
                command.startsAt(),
                command.endsAt(),
                0,
                now));
        audit.record(
                "STOREFRONT_BANNER",
                saved.id(),
                "CREATED",
                null,
                saved.version(),
                Map.of("status", saved.status().name()));
        return saved;
    }

    @Transactional
    public StorefrontBanner update(UUID id, long expectedVersion, SaveCommand command) {
        var current = get(id);
        if (current.version() != expectedVersion) {
            throw new StaleCatalogVersionException();
        }
        verifyMedia(command, id);
        var saved = repository.save(current.revise(
                command.internalName(),
                command.eyebrow(),
                command.title(),
                command.description(),
                command.buttonLabel(),
                command.targetType(),
                command.targetValue(),
                command.desktopObjectKey(),
                command.mobileObjectKey(),
                command.status(),
                command.displayOrder(),
                command.startsAt(),
                command.endsAt(),
                clock.instant()));
        audit.record(
                "STOREFRONT_BANNER",
                saved.id(),
                "UPDATED",
                current.version(),
                saved.version(),
                Map.of(
                        "statusFrom",
                        current.status().name(),
                        "statusTo",
                        saved.status().name()));
        return saved;
    }

    private void verifyMedia(SaveCommand command, @Nullable UUID existingId) {
        if (command.desktopObjectKey() != null) {
            media.verifyBanner(existingId, command.desktopObjectKey());
        }
        if (command.mobileObjectKey() != null) {
            media.verifyBanner(existingId, command.mobileObjectKey());
        }
    }

    public static String targetUrl(StorefrontBanner banner) {
        return switch (banner.targetType()) {
            case CATEGORY -> "/catalog/" + encode(banner.targetValue());
            case SUBCATEGORY -> {
                var parts = banner.targetValue().split("/", 2);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Subcategory target must be category/section");
                }
                yield "/catalog/" + encode(parts[0]) + "?section=" + encode(parts[1]);
            }
            case COLLECTION -> "/catalog/clothes?collection=" + encode(banner.targetValue());
            case SALE -> "/#sale";
            case URL -> banner.targetValue();
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record SaveCommand(
            String internalName,
            String eyebrow,
            String title,
            String description,
            String buttonLabel,
            StorefrontBanner.TargetType targetType,
            String targetValue,
            @Nullable String desktopObjectKey,
            @Nullable String mobileObjectKey,
            StorefrontBanner.Status status,
            int displayOrder,
            @Nullable Instant startsAt,
            @Nullable Instant endsAt) {}
}
