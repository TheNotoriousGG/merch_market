package ru.amra.market.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryStatus;

@Entity
@Table(name = "catalog_categories")
class CategoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "parent_id")
    private @Nullable UUID parentId;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug = "";

    @Column(name = "name", nullable = false, length = 160)
    private String name = "";

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CategoryStatus status = CategoryStatus.HIDDEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CategoryJpaEntity() {}

    CategoryJpaEntity(Category category) {
        id = category.id().value();
        apply(category);
    }

    void apply(Category category) {
        parentId = category.parentId().map(parent -> parent.value()).orElse(null);
        slug = category.slug().value();
        name = category.name().value();
        displayOrder = category.displayOrder();
        status = category.status();
    }

    boolean hasSameBusinessState(Category category) {
        return Objects.equals(
                        parentId,
                        category.parentId().map(parent -> parent.value()).orElse(null))
                && slug.equals(category.slug().value())
                && name.equals(category.name().value())
                && displayOrder == category.displayOrder()
                && status == category.status();
    }

    UUID id() {
        return id;
    }

    @Nullable UUID parentId() {
        return parentId;
    }

    String slug() {
        return slug;
    }

    String name() {
        return name;
    }

    int displayOrder() {
        return displayOrder;
    }

    CategoryStatus status() {
        return status;
    }

    long version() {
        return version;
    }
}
