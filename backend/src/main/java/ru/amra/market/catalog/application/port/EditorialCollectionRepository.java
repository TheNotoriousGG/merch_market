package ru.amra.market.catalog.application.port;

import java.util.Optional;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.EditorialCollection;

/** Persistence boundary for editorial collection aggregates. */
public interface EditorialCollectionRepository {

    EditorialCollection save(EditorialCollection collection);

    Optional<EditorialCollection> findById(CollectionId id);
}
