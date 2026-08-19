package ru.amra.market.catalog.infrastructure.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.ProductLookup;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductSlug;

@Repository
class JpaProductRepositoryAdapter implements ProductRepository {

    private final ProductJpaRepository repository;
    private final ProductChildJdbcStore children;

    JpaProductRepositoryAdapter(ProductJpaRepository repository, ProductChildJdbcStore children) {
        this.repository = repository;
        this.children = children;
    }

    @Override
    @Transactional
    public Product save(Product product) {
        children.assertSlugNamespace(product);
        var stored = repository.findById(product.id().value());
        if (stored.isEmpty()) {
            if (product.version() != 0) {
                throw stale(product, -1);
            }
            var entity = repository.saveAndFlush(new ProductJpaEntity(product));
            children.synchronize(product);
            return assemble(entity);
        }

        var entity = stored.orElseThrow();
        var current = assemble(entity);
        if (product.version() == entity.version() && ProductState.from(product).equals(ProductState.from(current))) {
            return current;
        }
        if (product.version() != entity.version() + 1) {
            throw stale(product, entity.version());
        }
        entity.apply(product);
        repository.flush();
        children.synchronize(product);
        return assemble(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(ProductId id) {
        return repository.findById(id.value()).map(this::assemble);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductLookup> findBySlug(ProductSlug slug) {
        var canonical = repository.findByCanonicalSlugIgnoreCase(slug.value());
        if (canonical.isPresent()) {
            return canonical.map(entity -> new ProductLookup(assemble(entity), false));
        }
        return children.findProductIdByAlias(slug)
                .flatMap(id -> repository.findById(id.value()))
                .map(entity -> new ProductLookup(assemble(entity), true));
    }

    private Product assemble(ProductJpaEntity entity) {
        return ProductPersistenceMapper.toDomain(entity, children.load(new ProductId(entity.id())));
    }

    private static ConcurrentCatalogModificationException stale(Product product, long storedVersion) {
        return new ConcurrentCatalogModificationException("Product " + product.id() + " version " + product.version()
                + " does not follow stored version " + storedVersion);
    }
}
