package ru.amra.market.catalog.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Aggregate owning editorial collection metadata and deterministic product order. */
public final class EditorialCollection {

    private final CollectionId id;
    private final CollectionSlug slug;
    private final String name;
    private final String description;
    private final CollectionStatus status;
    private final int displayOrder;
    private final List<ProductId> productIds;
    private final long version;

    private EditorialCollection(
            CollectionId id,
            CollectionSlug slug,
            String name,
            String description,
            CollectionStatus status,
            int displayOrder,
            List<ProductId> productIds,
            long version) {
        this.id = Objects.requireNonNull(id);
        this.slug = Objects.requireNonNull(slug);
        this.name = required(name, 160, "name");
        this.description = required(description, 2_000, "description");
        this.status = Objects.requireNonNull(status);
        if (displayOrder < 0) {
            throw new CollectionInvariantViolation(
                    CollectionInvariant.INVALID_ORDER, "Collection display order must not be negative");
        }
        this.displayOrder = displayOrder;
        this.productIds = products(productIds);
        if (version < 0) {
            throw new CollectionInvariantViolation(
                    CollectionInvariant.INVALID_VERSION, "Collection version must not be negative");
        }
        this.version = version;
    }

    /** Creates a hidden editorial collection. */
    public static EditorialCollection create(
            CollectionId id, CollectionSlug slug, String name, String description, int displayOrder) {
        return new EditorialCollection(
                id, slug, name, description, CollectionStatus.HIDDEN, displayOrder, List.of(), 0);
    }

    /** Restores persisted collection state without applying a command. */
    public static EditorialCollection restore(
            CollectionId id,
            CollectionSlug slug,
            String name,
            String description,
            CollectionStatus status,
            int displayOrder,
            List<ProductId> productIds,
            long version) {
        return new EditorialCollection(id, slug, name, description, status, displayOrder, productIds, version);
    }

    /** Revises collection metadata as one optimistic version change. */
    public EditorialCollection revise(
            CollectionSlug newSlug,
            String newName,
            String newDescription,
            CollectionStatus newStatus,
            int newDisplayOrder) {
        if (slug.equals(newSlug)
                && name.equals(newName)
                && description.equals(newDescription)
                && status == newStatus
                && displayOrder == newDisplayOrder) {
            return this;
        }
        return new EditorialCollection(
                id, newSlug, newName, newDescription, newStatus, newDisplayOrder, productIds, version + 1);
    }

    /** Replaces the complete editorial membership order. */
    public EditorialCollection replaceProducts(List<ProductId> newProductIds) {
        var validated = products(newProductIds);
        if (productIds.equals(validated)) {
            return this;
        }
        return new EditorialCollection(id, slug, name, description, status, displayOrder, validated, version + 1);
    }

    public CollectionId id() {
        return id;
    }

    public CollectionSlug slug() {
        return slug;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public CollectionStatus status() {
        return status;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public List<ProductId> productIds() {
        return productIds;
    }

    public long version() {
        return version;
    }

    private static List<ProductId> products(List<ProductId> source) {
        var result = List.copyOf(source);
        if (result.stream().anyMatch(Objects::isNull) || new HashSet<>(result).size() != result.size()) {
            throw new CollectionInvariantViolation(
                    CollectionInvariant.DUPLICATE_PRODUCT,
                    "Collection product membership must contain unique non-null identifiers");
        }
        return result;
    }

    private static String required(String value, int maximumLength, String field) {
        if (value == null || value.isBlank() || value.strip().length() > maximumLength) {
            throw new CollectionInvariantViolation(
                    CollectionInvariant.INVALID_CONTENT,
                    "Collection " + field + " must contain 1-" + maximumLength + " characters");
        }
        return value.strip();
    }
}
