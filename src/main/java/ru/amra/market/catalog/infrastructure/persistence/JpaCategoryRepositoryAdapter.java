package ru.amra.market.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;

@Repository
class JpaCategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository repository;

    JpaCategoryRepositoryAdapter(CategoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Category save(Category category) {
        var stored = repository.findById(category.id().value());
        if (stored.isEmpty()) {
            if (category.version() != 0) {
                throw stale(category, -1);
            }
            return CategoryPersistenceMapper.toDomain(repository.saveAndFlush(new CategoryJpaEntity(category)));
        }

        var entity = stored.orElseThrow();
        if (category.version() == entity.version() && entity.hasSameBusinessState(category)) {
            return CategoryPersistenceMapper.toDomain(entity);
        }
        if (category.version() != entity.version() + 1) {
            throw stale(category, entity.version());
        }
        entity.apply(category);
        repository.flush();
        return CategoryPersistenceMapper.toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(CategoryId id) {
        return repository.findById(id.value()).map(CategoryPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return repository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(CategoryPersistenceMapper::toDomain)
                .toList();
    }

    private static ConcurrentCatalogModificationException stale(Category category, long storedVersion) {
        return new ConcurrentCatalogModificationException("Category " + category.id() + " version " + category.version()
                + " does not follow stored version " + storedVersion);
    }
}
