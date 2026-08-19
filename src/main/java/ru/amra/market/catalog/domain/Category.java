package ru.amra.market.catalog.domain;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Immutable category aggregate. Tree-wide rules are enforced by {@link CategoryHierarchy}. */
public final class Category {

    private final CategoryId id;
    private final @Nullable CategoryId parentId;
    private final CategorySlug slug;
    private final CategoryName name;
    private final int displayOrder;
    private final CategoryStatus status;
    private final long version;

    private Category(
            CategoryId id,
            @Nullable CategoryId parentId,
            CategorySlug slug,
            CategoryName name,
            int displayOrder,
            CategoryStatus status,
            long version) {
        if (displayOrder < 0) {
            throw new CategoryInvariantViolation(
                    CategoryInvariant.INVALID_DISPLAY_ORDER, "Category display order must not be negative");
        }
        if (version < 0) {
            throw new CategoryInvariantViolation(
                    CategoryInvariant.INVALID_VERSION, "Category version must not be negative");
        }
        if (id.equals(parentId)) {
            throw new CategoryInvariantViolation(CategoryInvariant.SELF_PARENT, "Category cannot be its own parent");
        }
        this.id = id;
        this.parentId = parentId;
        this.slug = slug;
        this.name = name;
        this.displayOrder = displayOrder;
        this.status = status;
        this.version = version;
    }

    /** Creates a hidden category so incomplete navigation cannot become public accidentally. */
    public static Category create(
            CategoryId id, @Nullable CategoryId parentId, CategorySlug slug, CategoryName name, int displayOrder) {
        return new Category(id, parentId, slug, name, displayOrder, CategoryStatus.HIDDEN, 0);
    }

    /** Restores a persisted category without applying a business mutation. */
    public static Category restore(
            CategoryId id,
            @Nullable CategoryId parentId,
            CategorySlug slug,
            CategoryName name,
            int displayOrder,
            CategoryStatus status,
            long version) {
        return new Category(id, parentId, slug, name, displayOrder, status, version);
    }

    /** Returns a copy with changed editable attributes and an incremented version. */
    public Category change(
            @Nullable CategoryId newParentId,
            CategorySlug newSlug,
            CategoryName newName,
            int newDisplayOrder,
            CategoryStatus newStatus) {
        if (Objects.equals(parentId, newParentId)
                && slug.equals(newSlug)
                && name.equals(newName)
                && displayOrder == newDisplayOrder
                && status == newStatus) {
            return this;
        }
        return new Category(id, newParentId, newSlug, newName, newDisplayOrder, newStatus, version + 1);
    }

    public CategoryId id() {
        return id;
    }

    public Optional<CategoryId> parentId() {
        return Optional.ofNullable(parentId);
    }

    public CategorySlug slug() {
        return slug;
    }

    public CategoryName name() {
        return name;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public CategoryStatus status() {
        return status;
    }

    public long version() {
        return version;
    }
}
