package ru.amra.market.catalog.application.port;

import java.util.Set;
import ru.amra.market.catalog.domain.CategoryId;

/** Resolves the active subset needed by product publication validation. */
public interface ActiveCategoryReader {

    Set<CategoryId> findActive(Set<CategoryId> candidates);
}
