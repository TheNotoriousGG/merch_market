package ru.amra.market.catalog.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.StorefrontBannerRepository;
import ru.amra.market.catalog.domain.StorefrontBanner;

/** JDBC adapter for the compact storefront banner aggregate. */
@Repository
class JdbcStorefrontBannerRepository implements StorefrontBannerRepository {
    private static final String COLUMNS = """
            id, internal_name, eyebrow, title, description, button_label, target_type, target_value,
            desktop_object_key, mobile_object_key, status, display_order, starts_at, ends_at, version, updated_at
            """;
    private final JdbcTemplate jdbc;

    JdbcStorefrontBannerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<StorefrontBanner> findAll() {
        return jdbc.query("select " + COLUMNS + " from storefront_banners order by display_order, id", this::map);
    }

    @Override
    public List<StorefrontBanner> findPublishedAt(Instant now) {
        return jdbc.query("""
                select %s from storefront_banners
                where status = 'PUBLISHED'
                  and (starts_at is null or starts_at <= ?)
                  and (ends_at is null or ends_at > ?)
                order by display_order, id limit 10
                """.formatted(COLUMNS), this::map, Timestamp.from(now), Timestamp.from(now));
    }

    @Override
    public Optional<StorefrontBanner> findById(UUID id) {
        return jdbc.query("select " + COLUMNS + " from storefront_banners where id = ?", this::map, id).stream()
                .findFirst();
    }

    @Override
    public StorefrontBanner save(StorefrontBanner banner) {
        var current = findById(banner.id());
        if (current.isEmpty()) {
            jdbc.update(
                    """
                    insert into storefront_banners (
                      id, internal_name, eyebrow, title, description, button_label, target_type, target_value,
                      desktop_object_key, mobile_object_key, status, display_order, starts_at, ends_at, version
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    banner.id(),
                    banner.internalName(),
                    banner.eyebrow(),
                    banner.title(),
                    banner.description(),
                    banner.buttonLabel(),
                    banner.targetType().name(),
                    banner.targetValue(),
                    banner.desktopObjectKey(),
                    banner.mobileObjectKey(),
                    banner.status().name(),
                    banner.displayOrder(),
                    timestamp(banner.startsAt()),
                    timestamp(banner.endsAt()));
            return findById(banner.id()).orElseThrow();
        }
        var stored = current.orElseThrow();
        if (banner.version() != stored.version() + 1) {
            throw stale(banner.id());
        }
        var count = jdbc.update(
                """
                update storefront_banners set internal_name=?, eyebrow=?, title=?, description=?, button_label=?,
                  target_type=?, target_value=?, desktop_object_key=?, mobile_object_key=?, status=?, display_order=?,
                  starts_at=?, ends_at=?, updated_at=current_timestamp, version=version+1
                where id=? and version=?
                """,
                banner.internalName(),
                banner.eyebrow(),
                banner.title(),
                banner.description(),
                banner.buttonLabel(),
                banner.targetType().name(),
                banner.targetValue(),
                banner.desktopObjectKey(),
                banner.mobileObjectKey(),
                banner.status().name(),
                banner.displayOrder(),
                timestamp(banner.startsAt()),
                timestamp(banner.endsAt()),
                banner.id(),
                stored.version());
        if (count != 1) {
            throw stale(banner.id());
        }
        return findById(banner.id()).orElseThrow();
    }

    private StorefrontBanner map(ResultSet result, int row) throws SQLException {
        var starts = result.getTimestamp("starts_at");
        var ends = result.getTimestamp("ends_at");
        return new StorefrontBanner(
                result.getObject("id", UUID.class),
                result.getString("internal_name"),
                result.getString("eyebrow"),
                result.getString("title"),
                result.getString("description"),
                result.getString("button_label"),
                StorefrontBanner.TargetType.valueOf(result.getString("target_type")),
                result.getString("target_value"),
                result.getString("desktop_object_key"),
                result.getString("mobile_object_key"),
                StorefrontBanner.Status.valueOf(result.getString("status")),
                result.getInt("display_order"),
                starts == null ? null : starts.toInstant(),
                ends == null ? null : ends.toInstant(),
                result.getLong("version"),
                result.getTimestamp("updated_at").toInstant());
    }

    private static ConcurrentCatalogModificationException stale(UUID id) {
        return new ConcurrentCatalogModificationException("Storefront banner " + id + " changed concurrently");
    }

    private static @Nullable Timestamp timestamp(@Nullable Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
