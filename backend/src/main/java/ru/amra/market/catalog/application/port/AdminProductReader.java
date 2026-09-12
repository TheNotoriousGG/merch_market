package ru.amra.market.catalog.application.port;

import java.time.Instant;
import java.util.Optional;
import ru.amra.market.catalog.domain.ProductId;

/** Read boundary for administrative root persistence metadata. */
public interface AdminProductReader {

    Optional<Metadata> findMetadata(ProductId id);

    record Metadata(Instant createdAt, Instant updatedAt) {}
}
