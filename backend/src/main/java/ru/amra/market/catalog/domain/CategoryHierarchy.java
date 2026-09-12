package ru.amra.market.catalog.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validated immutable snapshot of the category hierarchy. */
public final class CategoryHierarchy {

    /** Approved maximum number of nodes from a root through a leaf, inclusive. */
    public static final int MAX_DEPTH = 5;

    private static final Comparator<Category> NAVIGATION_ORDER =
            Comparator.comparingInt(Category::displayOrder).thenComparing(Category::id);

    private final Map<CategoryId, Category> categories;

    /**
     * Builds and validates a complete prospective hierarchy snapshot.
     *
     * @param source categories after the proposed mutation
     */
    public CategoryHierarchy(Collection<Category> source) {
        categories = index(source);
        validateParentsAndSiblingSlugs();
        categories.keySet().forEach(this::validatePathToRoot);
    }

    /** Returns roots in deterministic navigation order. */
    public List<Category> roots() {
        return ordered(categories.values().stream()
                .filter(category -> category.parentId().isEmpty())
                .toList());
    }

    /** Returns direct children in deterministic navigation order. */
    public List<Category> childrenOf(CategoryId parentId) {
        requireExisting(parentId);
        return ordered(categories.values().stream()
                .filter(category -> category.parentId().filter(parentId::equals).isPresent())
                .toList());
    }

    private static Map<CategoryId, Category> index(Collection<Category> source) {
        var result = new HashMap<CategoryId, Category>();
        for (var category : source) {
            if (result.put(category.id(), category) != null) {
                throw new CategoryInvariantViolation(
                        CategoryInvariant.INVALID_ID, "Category hierarchy contains a duplicate identifier");
            }
        }
        return Map.copyOf(result);
    }

    private void validateParentsAndSiblingSlugs() {
        var siblingSlugs = new HashSet<SiblingSlug>();
        for (var category : categories.values()) {
            category.parentId().ifPresent(this::requireExisting);
            var key = new SiblingSlug(category.parentId().orElse(null), category.slug());
            if (!siblingSlugs.add(key)) {
                throw new CategoryInvariantViolation(
                        CategoryInvariant.DUPLICATE_SIBLING_SLUG,
                        "Sibling categories must have unique normalized slugs");
            }
        }
    }

    private void validatePathToRoot(CategoryId startingId) {
        var visited = new HashSet<CategoryId>();
        var currentId = startingId;
        var depth = 0;
        while (true) {
            if (!visited.add(currentId)) {
                throw new CategoryInvariantViolation(
                        CategoryInvariant.CATEGORY_CYCLE, "Category hierarchy has a cycle");
            }
            depth++;
            if (depth > MAX_DEPTH) {
                throw new CategoryInvariantViolation(
                        CategoryInvariant.CATEGORY_DEPTH_EXCEEDED,
                        "Category hierarchy cannot exceed " + MAX_DEPTH + " levels");
            }
            var parent = Objects.requireNonNull(categories.get(currentId)).parentId();
            if (parent.isEmpty()) {
                return;
            }
            currentId = parent.orElseThrow();
        }
    }

    private void requireExisting(CategoryId id) {
        if (!categories.containsKey(id)) {
            throw new CategoryInvariantViolation(
                    CategoryInvariant.ORPHAN_PARENT, "Category parent must exist in the hierarchy snapshot");
        }
    }

    private static List<Category> ordered(Collection<Category> source) {
        var result = new ArrayList<>(source);
        result.sort(NAVIGATION_ORDER);
        return List.copyOf(result);
    }

    private record SiblingSlug(
            @org.jspecify.annotations.Nullable CategoryId parentId, CategorySlug slug) {}
}
