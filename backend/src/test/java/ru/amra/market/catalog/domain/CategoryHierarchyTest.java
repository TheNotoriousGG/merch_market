package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryHierarchyTest {

    @Test
    void returnsRootsAndChildrenInStableNavigationOrder() {
        var rootLaterId = category(2, null, "accessories", 10);
        var rootEarlierId = category(1, null, "clothes", 10);
        var childLaterOrder = category(4, rootEarlierId.id(), "hoodies", 20);
        var childEarlierOrder = category(3, rootEarlierId.id(), "shirts", 5);
        var hierarchy = new CategoryHierarchy(List.of(rootLaterId, childLaterOrder, rootEarlierId, childEarlierOrder));

        assertThat(hierarchy.roots())
                .extracting(Category::slug)
                .containsExactly(new CategorySlug("clothes"), new CategorySlug("accessories"));
        assertThat(hierarchy.childrenOf(rootEarlierId.id()))
                .extracting(Category::slug)
                .containsExactly(new CategorySlug("shirts"), new CategorySlug("hoodies"));
    }

    @Test
    void rejectsOrphanParent() {
        var orphan = category(1, id(99), "orphan", 0);

        assertViolation(CategoryInvariant.ORPHAN_PARENT, () -> new CategoryHierarchy(List.of(orphan)));
    }

    @Test
    void rejectsCyclesOfAnyLength() {
        var first = category(1, id(2), "first", 0);
        var second = category(2, id(3), "second", 0);
        var third = category(3, id(1), "third", 0);

        assertViolation(CategoryInvariant.CATEGORY_CYCLE, () -> new CategoryHierarchy(List.of(first, second, third)));
    }

    @Test
    void acceptsFiveLevelsAndRejectsSix() {
        var categories = new ArrayList<Category>();
        for (int level = 1; level <= CategoryHierarchy.MAX_DEPTH; level++) {
            categories.add(category(level, level == 1 ? null : id(level - 1), "level-" + level, level));
        }

        new CategoryHierarchy(categories);
        categories.add(category(6, id(5), "level-6", 6));

        assertViolation(CategoryInvariant.CATEGORY_DEPTH_EXCEEDED, () -> new CategoryHierarchy(categories));
    }

    @Test
    void rejectsDuplicateNormalizedSlugOnlyAmongSiblings() {
        var firstRoot = category(1, null, "clothes", 0);
        var duplicateRoot = category(2, null, "CLOTHES", 1);

        assertViolation(
                CategoryInvariant.DUPLICATE_SIBLING_SLUG,
                () -> new CategoryHierarchy(List.of(firstRoot, duplicateRoot)));

        var secondRoot = category(3, null, "accessories", 2);
        var childWithSameSlug = category(4, secondRoot.id(), "clothes", 0);
        new CategoryHierarchy(List.of(firstRoot, secondRoot, childWithSameSlug));
    }

    private static Category category(
            long id, @org.jspecify.annotations.Nullable CategoryId parentId, String slug, int displayOrder) {
        return Category.create(
                id(id), parentId, new CategorySlug(slug), new CategoryName("Category " + id), displayOrder);
    }

    private static CategoryId id(long value) {
        return new CategoryId(new UUID(0, value));
    }

    private static void assertViolation(CategoryInvariant invariant, Runnable command) {
        assertThatThrownBy(command::run)
                .isInstanceOfSatisfying(
                        CategoryInvariantViolation.class,
                        violation -> assertThat(violation.invariant()).isEqualTo(invariant));
    }
}
