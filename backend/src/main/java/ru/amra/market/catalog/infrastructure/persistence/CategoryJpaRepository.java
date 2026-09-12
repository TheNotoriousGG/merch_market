package ru.amra.market.catalog.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    List<CategoryJpaEntity> findAllByOrderByDisplayOrderAscIdAsc();
}
