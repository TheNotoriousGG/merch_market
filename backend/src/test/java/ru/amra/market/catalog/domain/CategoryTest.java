package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CategoryTest {

    private static final CategoryId ID = new CategoryId(new UUID(0, 1));

    @Test
    void newCategoryIsHiddenAndStartsAtVersionZero() {
        var category = Category.create(ID, null, new CategorySlug("odezhda"), new CategoryName(" Одежда "), 10);

        assertThat(category.status()).isEqualTo(CategoryStatus.HIDDEN);
        assertThat(category.name().value()).isEqualTo("Одежда");
        assertThat(category.version()).isZero();
        assertThat(category.parentId()).isEmpty();
    }

    @Test
    void businessChangeProducesANewVersionedAggregate() {
        var original = Category.create(ID, null, new CategorySlug("odezhda"), new CategoryName("Одежда"), 10);
        var parent = new CategoryId(new UUID(0, 2));

        var changed = original.change(
                parent, new CategorySlug("clothes"), new CategoryName("Clothes"), 20, CategoryStatus.ACTIVE);

        assertThat(changed).isNotSameAs(original);
        assertThat(changed.parentId()).contains(parent);
        assertThat(changed.slug().value()).isEqualTo("clothes");
        assertThat(changed.status()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(changed.version()).isEqualTo(1);
    }

    @Test
    void identicalChangeIsANoopAndDoesNotAdvanceVersion() {
        var category = Category.create(ID, null, new CategorySlug("odezhda"), new CategoryName("Одежда"), 10);

        var unchanged =
                category.change(null, category.slug(), category.name(), category.displayOrder(), category.status());

        assertThat(unchanged).isSameAs(category);
        assertThat(unchanged.version()).isZero();
    }

    @Test
    void rejectsSelfParentAndNegativeOrdering() {
        assertThatThrownBy(() -> Category.create(ID, ID, new CategorySlug("odezhda"), new CategoryName("Одежда"), 0))
                .isInstanceOfSatisfying(
                        CategoryInvariantViolation.class,
                        violation -> assertThat(violation.invariant()).isEqualTo(CategoryInvariant.SELF_PARENT));

        assertThatThrownBy(() -> Category.create(ID, null, new CategorySlug("odezhda"), new CategoryName("Одежда"), -1))
                .isInstanceOfSatisfying(
                        CategoryInvariantViolation.class,
                        violation ->
                                assertThat(violation.invariant()).isEqualTo(CategoryInvariant.INVALID_DISPLAY_ORDER));
    }
}
