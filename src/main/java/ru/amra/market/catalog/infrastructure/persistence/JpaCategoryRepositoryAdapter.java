package ru.amra.market.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;

@Repository
class JpaCategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository repository;
    private final JdbcTemplate jdbc;

    JpaCategoryRepositoryAdapter(CategoryJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
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

    @Override
    @Transactional
    public boolean deleteIfUnused(CategoryId id) {
        var references = jdbc.queryForObject(
                """
                select exists(select 1 from catalog_categories where parent_id = ?)
                    or exists(select 1 from catalog_product_categories where category_id = ?)
                """,
                Boolean.class,
                id.value(),
                id.value());
        if (Boolean.TRUE.equals(references)) {
            return false;
        }
        repository.deleteById(id.value());
        repository.flush();
        return true;
    }

    private static ConcurrentCatalogModificationException stale(Category category, long storedVersion) {
        return new ConcurrentCatalogModificationException("Category " + category.id() + " version " + category.version()
                + " does not follow stored version " + storedVersion);
    }
}
