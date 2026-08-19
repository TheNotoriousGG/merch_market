package ru.amra.market.catalog.domain;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Product media metadata; public delivery URLs are created by an adapter. */
public final class ProductMedia {

    private final MediaId id;
    private final @Nullable VariantId variantId;
    private final MediaType type;
    private final String objectKey;
    private final String contentType;
    private final int width;
    private final int height;
    private final String alt;
    private final int displayOrder;
    private final boolean primary;
    private final long version;

    private ProductMedia(
            MediaId id,
            @Nullable VariantId variantId,
            MediaType type,
            String objectKey,
            String contentType,
            int width,
            int height,
            String alt,
            int displayOrder,
            boolean primary,
            long version) {
        this.id = id;
        this.variantId = variantId;
        this.type = type;
        this.objectKey = required(objectKey, 512, "object key");
        this.contentType = required(contentType, 100, "content type");
        if (!this.contentType.startsWith("image/")) {
            throw invalid("Product media content type must be an image");
        }
        if (width < 1 || height < 1) {
            throw invalid("Product media dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.alt = required(alt, 300, "alt text");
        if (displayOrder < 0) {
            throw invalid("Product media display order must not be negative");
        }
        this.displayOrder = displayOrder;
        this.primary = primary;
        if (version < 0) {
            throw new ProductInvariantViolation(ProductInvariant.INVALID_VERSION, "Media version must not be negative");
        }
        this.version = version;
    }

    /** Creates image metadata without exposing storage details to public DTOs. */
    public static ProductMedia image(
            MediaId id,
            @Nullable VariantId variantId,
            String objectKey,
            String contentType,
            int width,
            int height,
            String alt,
            int displayOrder,
            boolean primary) {
        return new ProductMedia(
                id, variantId, MediaType.IMAGE, objectKey, contentType, width, height, alt, displayOrder, primary, 0);
    }

    /** Restores persisted image metadata without applying a business mutation. */
    public static ProductMedia restoreImage(
            MediaId id,
            @Nullable VariantId variantId,
            String objectKey,
            String contentType,
            int width,
            int height,
            String alt,
            int displayOrder,
            boolean primary,
            long version) {
        return new ProductMedia(
                id,
                variantId,
                MediaType.IMAGE,
                objectKey,
                contentType,
                width,
                height,
                alt,
                displayOrder,
                primary,
                version);
    }

    public MediaId id() {
        return id;
    }

    public Optional<VariantId> variantId() {
        return Optional.ofNullable(variantId);
    }

    public MediaType type() {
        return type;
    }

    public String objectKey() {
        return objectKey;
    }

    public String contentType() {
        return contentType;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String alt() {
        return alt;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public boolean primary() {
        return primary;
    }

    public long version() {
        return version;
    }

    private static String required(String candidate, int maximumLength, String field) {
        if (candidate == null || candidate.isBlank() || candidate.strip().length() > maximumLength) {
            throw invalid("Product media " + field + " is invalid");
        }
        return candidate.strip();
    }

    private static ProductInvariantViolation invalid(String message) {
        return new ProductInvariantViolation(ProductInvariant.INVALID_MEDIA, message);
    }
}
