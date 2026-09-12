package ru.amra.market.catalog.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    @Query("select product from ProductJpaEntity product where lower(product.canonicalSlug) = lower(:slug)")
    Optional<ProductJpaEntity> findByCanonicalSlugIgnoreCase(@Param("slug") String slug);
}
