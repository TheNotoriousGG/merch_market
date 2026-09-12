package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ru.amra.market.catalog.application.ManageStorefrontBanners;
import ru.amra.market.catalog.domain.StorefrontBanner.Status;
import ru.amra.market.catalog.domain.StorefrontBanner.TargetType;

class StorefrontBannerTest {

    private static final UUID ID = UUID.fromString("01991a80-0000-7000-8000-000000000201");
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    @Test
    void normalizesContentAndAdvancesVersionOnRevision() {
        var draft = banner(StorefrontBanner.Status.DRAFT, null, null);

        var published = draft.revise(
                "  Главная  ",
                "  Новинка  ",
                "  Худи AMRA  ",
                "  Новая коллекция  ",
                "  Смотреть  ",
                StorefrontBanner.TargetType.CATEGORY,
                "  clothes  ",
                "banners/01991a80-0000-7000-8000-000000000201/uploads/hero.webp",
                null,
                StorefrontBanner.Status.PUBLISHED,
                2,
                null,
                null,
                NOW.plusSeconds(60));

        assertThat(published.internalName()).isEqualTo("Главная");
        assertThat(published.targetValue()).isEqualTo("clothes");
        assertThat(published.status()).isEqualTo(StorefrontBanner.Status.PUBLISHED);
        assertThat(published.version()).isEqualTo(1);
        assertThat(published.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void rejectsPublishedBannerWithoutDesktopImageAndInvalidSchedule() {
        var draft = banner(StorefrontBanner.Status.DRAFT, null, null);
        assertThatThrownBy(() -> draft.revise(
                        draft.internalName(),
                        draft.eyebrow(),
                        draft.title(),
                        draft.description(),
                        draft.buttonLabel(),
                        draft.targetType(),
                        draft.targetValue(),
                        null,
                        null,
                        StorefrontBanner.Status.PUBLISHED,
                        draft.displayOrder(),
                        null,
                        null,
                        NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desktop image");
        assertThatThrownBy(() -> banner(StorefrontBanner.Status.DRAFT, NOW.plusSeconds(60), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end must be after start");
    }

    @Test
    void archivedBannerCannotReturnToAnEditableLifecycle() {
        var archived = banner(StorefrontBanner.Status.ARCHIVED, null, null);

        assertThatThrownBy(() -> archived.revise(
                        archived.internalName(),
                        archived.eyebrow(),
                        archived.title(),
                        archived.description(),
                        archived.buttonLabel(),
                        archived.targetType(),
                        archived.targetValue(),
                        null,
                        null,
                        StorefrontBanner.Status.DRAFT,
                        archived.displayOrder(),
                        null,
                        null,
                        NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be restored");
    }

    @Test
    void resolvesEverySupportedTargetWithoutExposingUnescapedInput() {
        assertThat(ManageStorefrontBanners.targetUrl(bannerWithTarget(TargetType.CATEGORY, "new arrivals")))
                .isEqualTo("/catalog/new%20arrivals");
        assertThat(ManageStorefrontBanners.targetUrl(bannerWithTarget(TargetType.SUBCATEGORY, "clothes/hoodies")))
                .isEqualTo("/catalog/clothes?section=hoodies");
        assertThat(ManageStorefrontBanners.targetUrl(bannerWithTarget(TargetType.COLLECTION, "week picks")))
                .isEqualTo("/catalog/clothes?collection=week%20picks");
        assertThat(ManageStorefrontBanners.targetUrl(bannerWithTarget(TargetType.SALE, "sale")))
                .isEqualTo("/#sale");
        assertThat(ManageStorefrontBanners.targetUrl(bannerWithTarget(TargetType.URL, "/account")))
                .isEqualTo("/account");
        assertThatThrownBy(() -> ManageStorefrontBanners.targetUrl(
                        bannerWithTarget(TargetType.SUBCATEGORY, "missing-separator")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidTextOrderAndVersion() {
        var valid = banner(Status.DRAFT, null, null);
        assertThatThrownBy(() -> new StorefrontBanner(
                        valid.id(),
                        " ",
                        valid.eyebrow(),
                        valid.title(),
                        valid.description(),
                        valid.buttonLabel(),
                        valid.targetType(),
                        valid.targetValue(),
                        null,
                        null,
                        valid.status(),
                        0,
                        null,
                        null,
                        0,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StorefrontBanner(
                        valid.id(),
                        valid.internalName(),
                        valid.eyebrow(),
                        valid.title(),
                        valid.description(),
                        valid.buttonLabel(),
                        valid.targetType(),
                        valid.targetValue(),
                        null,
                        null,
                        valid.status(),
                        -1,
                        null,
                        null,
                        -1,
                        NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StorefrontBanner bannerWithTarget(TargetType type, String value) {
        var banner = banner(Status.DRAFT, null, null);
        return banner.revise(
                banner.internalName(),
                banner.eyebrow(),
                banner.title(),
                banner.description(),
                banner.buttonLabel(),
                type,
                value,
                null,
                null,
                banner.status(),
                banner.displayOrder(),
                null,
                null,
                NOW.plusSeconds(1));
    }

    private static StorefrontBanner banner(
            StorefrontBanner.Status status, @Nullable Instant startsAt, @Nullable Instant endsAt) {
        return new StorefrontBanner(
                ID,
                "Главная",
                "Новинка",
                "Худи AMRA",
                "Новая коллекция",
                "Смотреть",
                StorefrontBanner.TargetType.CATEGORY,
                "clothes",
                status == StorefrontBanner.Status.PUBLISHED
                        ? "banners/01991a80-0000-7000-8000-000000000201/uploads/hero.webp"
                        : null,
                null,
                status,
                0,
                startsAt,
                endsAt,
                0,
                NOW);
    }
}
