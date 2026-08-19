package ru.amra.market.catalog.application.port;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryStatus;

/** Administrative category projection including persistence concurrency metadata. */
public interface AdminCategoryReader {

    Optional<Snapshot> findById(CategoryId id);

    record Snapshot(
            CategoryId id,
            @Nullable CategoryId parentId,
            String slug,
            String name,
            int displayOrder,
            CategoryStatus status,
            long version,
            Instant updatedAt) {}
}
