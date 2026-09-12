package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.StorefrontBannerRepository;
import ru.amra.market.catalog.domain.StorefrontBanner;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@Transactional
class StorefrontBannerRepositoryIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    @Autowired
    private StorefrontBannerRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsOnlyCurrentlyPublishedBannersInDisplayOrder() {
        var later = repository.save(banner("later", StorefrontBanner.Status.PUBLISHED, 20, null, null));
        var earlier = repository.save(banner("earlier", StorefrontBanner.Status.PUBLISHED, 10, null, null));
        var draft = repository.save(banner("draft", StorefrontBanner.Status.DRAFT, 0, null, null));
        var future = repository.save(banner("future", StorefrontBanner.Status.PUBLISHED, 0, NOW.plusSeconds(1), null));
        var expired = repository.save(banner("expired", StorefrontBanner.Status.PUBLISHED, 0, null, NOW));

        assertThat(repository.findPublishedAt(NOW))
                .extracting(StorefrontBanner::id)
                .containsSubsequence(earlier.id(), later.id())
                .doesNotContain(draft.id(), future.id(), expired.id());
    }

    @Test
    void roundTripsUpdatesAndRejectsAStaleVersion() {
        var created = repository.save(banner("editable", StorefrontBanner.Status.DRAFT, 0, null, null));
        var stale = repository.findById(created.id()).orElseThrow();
        var saved = repository.save(created.revise(
                created.internalName(),
                created.eyebrow(),
                "Обновлённый баннер",
                created.description(),
                created.buttonLabel(),
                created.targetType(),
                created.targetValue(),
                created.desktopObjectKey(),
                created.mobileObjectKey(),
                created.status(),
                created.displayOrder(),
                created.startsAt(),
                created.endsAt(),
                NOW.plusSeconds(1)));

        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.title()).isEqualTo("Обновлённый баннер");
        assertThatThrownBy(() -> repository.save(stale.revise(
                        stale.internalName(),
                        stale.eyebrow(),
                        "Потерянное изменение",
                        stale.description(),
                        stale.buttonLabel(),
                        stale.targetType(),
                        stale.targetValue(),
                        stale.desktopObjectKey(),
                        stale.mobileObjectKey(),
                        stale.status(),
                        stale.displayOrder(),
                        stale.startsAt(),
                        stale.endsAt(),
                        NOW.plusSeconds(2))))
                .isInstanceOf(ConcurrentCatalogModificationException.class);
    }

    private StorefrontBanner banner(
            String key,
            StorefrontBanner.Status status,
            int order,
            @Nullable Instant startsAt,
            @Nullable Instant endsAt) {
        var id = nextId();
        return new StorefrontBanner(
                id,
                key,
                "Коллекция",
                "Баннер " + key,
                "Описание",
                "Смотреть",
                StorefrontBanner.TargetType.CATEGORY,
                "clothes",
                status == StorefrontBanner.Status.PUBLISHED ? "banners/" + id + "/uploads/hero.webp" : null,
                null,
                status,
                order,
                startsAt,
                endsAt,
                0,
                NOW);
    }

    private UUID nextId() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
