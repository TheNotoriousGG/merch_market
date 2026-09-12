package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.catalog.application.CatalogVersionEtag;
import ru.amra.market.catalog.application.ManageStorefrontBanners;
import ru.amra.market.catalog.application.MediaUploadService;
import ru.amra.market.catalog.application.port.MediaDeliveryUrlProvider;
import ru.amra.market.catalog.domain.StorefrontBanner;
import ru.amra.market.platform.generated.api.StorefrontAdministrationApi;
import ru.amra.market.platform.generated.api.StorefrontApi;
import ru.amra.market.platform.generated.model.AdminStorefrontBannerDto;
import ru.amra.market.platform.generated.model.AdminStorefrontBannerListDto;
import ru.amra.market.platform.generated.model.CreateMediaUploadRequestDto;
import ru.amra.market.platform.generated.model.MediaUploadDto;
import ru.amra.market.platform.generated.model.SaveStorefrontBannerRequestDto;
import ru.amra.market.platform.generated.model.StorefrontBannerDto;
import ru.amra.market.platform.generated.model.StorefrontBannerListDto;

/** Generated-contract adapter for public and administrative storefront banners. */
@RestController
public final class StorefrontBannerController implements StorefrontApi, StorefrontAdministrationApi {
    private final ManageStorefrontBanners banners;
    private final MediaUploadService uploads;
    private final MediaDeliveryUrlProvider mediaUrls;

    public StorefrontBannerController(
            ManageStorefrontBanners banners, MediaUploadService uploads, MediaDeliveryUrlProvider mediaUrls) {
        this.banners = banners;
        this.uploads = uploads;
        this.mediaUrls = mediaUrls;
    }

    @Override
    public ResponseEntity<StorefrontBannerListDto> listStorefrontBanners() {
        return ResponseEntity.ok(new StorefrontBannerListDto(
                banners.listPublic().stream().map(this::publicDto).toList()));
    }

    @Override
    public ResponseEntity<AdminStorefrontBannerListDto> listAdminStorefrontBanners() {
        return ResponseEntity.ok(new AdminStorefrontBannerListDto(
                banners.listAdmin().stream().map(this::adminDto).toList()));
    }

    @Override
    public ResponseEntity<AdminStorefrontBannerDto> getAdminStorefrontBanner(UUID resourceId) {
        var banner = banners.get(resourceId);
        return ResponseEntity.ok()
                .eTag(CatalogVersionEtag.format(banner.version()))
                .body(adminDto(banner));
    }

    @Override
    public ResponseEntity<AdminStorefrontBannerDto> createStorefrontBanner(
            String csrf, SaveStorefrontBannerRequestDto request) {
        var saved = banners.create(command(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/admin/storefront/banners/" + saved.id()))
                .eTag(CatalogVersionEtag.format(saved.version()))
                .body(adminDto(saved));
    }

    @Override
    public ResponseEntity<AdminStorefrontBannerDto> updateStorefrontBanner(
            UUID resourceId, String ifMatch, String csrf, SaveStorefrontBannerRequestDto request) {
        var saved = banners.update(resourceId, CatalogVersionEtag.parse(ifMatch), command(request));
        return ResponseEntity.ok()
                .eTag(CatalogVersionEtag.format(saved.version()))
                .body(adminDto(saved));
    }

    @Override
    public ResponseEntity<MediaUploadDto> createStorefrontBannerUpload(
            UUID resourceId, String csrf, CreateMediaUploadRequestDto request) {
        banners.get(resourceId);
        var upload = uploads.prepareBanner(
                resourceId,
                requireNonNull(request.getFileName()),
                requireNonNull(request.getContentType()).getValue(),
                requireNonNull(request.getSize()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MediaUploadDto(
                        upload.objectKey(),
                        upload.uploadUrl(),
                        upload.expiresAt(),
                        upload.contentType(),
                        upload.maxSize()));
    }

    private ManageStorefrontBanners.SaveCommand command(SaveStorefrontBannerRequestDto request) {
        return new ManageStorefrontBanners.SaveCommand(
                requireNonNull(request.getInternalName()),
                requireNonNull(request.getEyebrow()),
                requireNonNull(request.getTitle()),
                requireNonNull(request.getDescription()),
                requireNonNull(request.getButtonLabel()),
                StorefrontBanner.TargetType.valueOf(
                        requireNonNull(request.getTargetType()).getValue()),
                requireNonNull(request.getTargetValue()),
                request.getDesktopObjectKey(),
                request.getMobileObjectKey(),
                StorefrontBanner.Status.valueOf(
                        requireNonNull(request.getStatus()).getValue()),
                requireNonNull(request.getDisplayOrder()),
                request.getStartsAt(),
                request.getEndsAt());
    }

    private StorefrontBannerDto publicDto(StorefrontBanner banner) {
        var dto = new StorefrontBannerDto(
                banner.id(),
                banner.eyebrow(),
                banner.title(),
                banner.description(),
                banner.buttonLabel(),
                ManageStorefrontBanners.targetUrl(banner),
                mediaUrls.publicUrl(banner.id(), requireNonNull(banner.desktopObjectKey())),
                banner.displayOrder());
        if (banner.mobileObjectKey() != null) {
            dto.setMobileImageUrl(mediaUrls.publicUrl(banner.id(), banner.mobileObjectKey()));
        }
        return dto;
    }

    private AdminStorefrontBannerDto adminDto(StorefrontBanner banner) {
        var dto = new AdminStorefrontBannerDto(
                banner.id(),
                banner.internalName(),
                banner.eyebrow(),
                banner.title(),
                banner.description(),
                banner.buttonLabel(),
                AdminStorefrontBannerDto.TargetTypeEnum.fromValue(
                        banner.targetType().name()),
                banner.targetValue(),
                AdminStorefrontBannerDto.StatusEnum.fromValue(banner.status().name()),
                banner.displayOrder(),
                banner.version(),
                banner.updatedAt());
        dto.setDesktopObjectKey(banner.desktopObjectKey());
        dto.setMobileObjectKey(banner.mobileObjectKey());
        if (banner.desktopObjectKey() != null) {
            dto.setDesktopImageUrl(mediaUrls.publicUrl(banner.id(), banner.desktopObjectKey()));
        }
        if (banner.mobileObjectKey() != null) {
            dto.setMobileImageUrl(mediaUrls.publicUrl(banner.id(), banner.mobileObjectKey()));
        }
        dto.setStartsAt(banner.startsAt());
        dto.setEndsAt(banner.endsAt());
        return dto;
    }
}
