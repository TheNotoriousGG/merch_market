package ru.amra.market.catalog.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Immutable storefront campaign configuration with explicit publication lifecycle. */
public record StorefrontBanner(
        UUID id,
        String internalName,
        String eyebrow,
        String title,
        String description,
        String buttonLabel,
        TargetType targetType,
        String targetValue,
        @Nullable String desktopObjectKey,
        @Nullable String mobileObjectKey,
        Status status,
        int displayOrder,
        @Nullable Instant startsAt,
        @Nullable Instant endsAt,
        long version,
        Instant updatedAt) {

    public StorefrontBanner {
        internalName = text(internalName, 160, "internal name");
        eyebrow = text(eyebrow, 80, "eyebrow");
        title = text(title, 160, "title");
        description = text(description, 300, "description");
        buttonLabel = text(buttonLabel, 80, "button label");
        targetValue = text(targetValue, 500, "target value");
        if (displayOrder < 0 || version < 0) {
            throw new IllegalArgumentException("Banner order and version must not be negative");
        }
        if (endsAt != null && startsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Banner end must be after start");
        }
        if (status == Status.PUBLISHED && desktopObjectKey == null) {
            throw new IllegalArgumentException("Published banner requires a desktop image");
        }
    }

    public StorefrontBanner revise(
            String nextInternalName,
            String nextEyebrow,
            String nextTitle,
            String nextDescription,
            String nextButtonLabel,
            TargetType nextTargetType,
            String nextTargetValue,
            @Nullable String nextDesktopObjectKey,
            @Nullable String nextMobileObjectKey,
            Status nextStatus,
            int nextDisplayOrder,
            @Nullable Instant nextStartsAt,
            @Nullable Instant nextEndsAt,
            Instant now) {
        if (status == Status.ARCHIVED && nextStatus != Status.ARCHIVED) {
            throw new IllegalArgumentException("Archived banner cannot be restored");
        }
        return new StorefrontBanner(
                id,
                nextInternalName,
                nextEyebrow,
                nextTitle,
                nextDescription,
                nextButtonLabel,
                nextTargetType,
                nextTargetValue,
                nextDesktopObjectKey,
                nextMobileObjectKey,
                nextStatus,
                nextDisplayOrder,
                nextStartsAt,
                nextEndsAt,
                version + 1,
                now);
    }

    private static String text(String value, int max, String field) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException("Banner " + field + " is invalid");
        }
        return normalized;
    }

    public enum Status {
        DRAFT,
        PUBLISHED,
        ARCHIVED
    }

    public enum TargetType {
        CATEGORY,
        SUBCATEGORY,
        COLLECTION,
        SALE,
        URL
    }
}
