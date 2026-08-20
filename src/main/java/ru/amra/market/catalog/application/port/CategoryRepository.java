package ru.amra.market.catalog.application.port;

import java.util.List;
import java.util.Optional;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;

/** Persistence boundary for category aggregates. */
public interface CategoryRepository {

    /** Inserts or optimistically updates a category. */
    Category save(Category category);

    /** Finds a category by stable identity. */
    Optional<Category> findById(CategoryId id);

    /** Loads the complete bounded hierarchy snapshot for invariant validation. */
    List<Category> findAll();

    /** Deletes a category only when no hierarchy or product reference exists. */
    boolean deleteIfUnused(CategoryId id);
}
