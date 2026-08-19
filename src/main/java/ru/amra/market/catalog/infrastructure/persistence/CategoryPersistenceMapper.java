package ru.amra.market.catalog.infrastructure.persistence;

import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryName;
import ru.amra.market.catalog.domain.CategorySlug;

final class CategoryPersistenceMapper {

    private CategoryPersistenceMapper() {}

    static Category toDomain(CategoryJpaEntity entity) {
        var storedParentId = entity.parentId();
        var parentId = storedParentId == null ? null : new CategoryId(storedParentId);
        return Category.restore(
                new CategoryId(entity.id()),
                parentId,
                new CategorySlug(entity.slug()),
                new CategoryName(entity.name()),
                entity.displayOrder(),
                entity.status(),
                entity.version());
    }
}
