package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditorialCollectionTest {

    @Test
    void createsHiddenAndVersionsMetadataAndMembershipIndependently() {
        var collection = EditorialCollection.create(
                new CollectionId(new UUID(0, 1)),
                new CollectionSlug("week-selection"),
                "Выбор недели",
                "Редакционная подборка",
                10);
        var product = new ProductId(new UUID(0, 2));

        var active = collection.revise(
                collection.slug(),
                collection.name(),
                collection.description(),
                CollectionStatus.ACTIVE,
                collection.displayOrder());
        var populated = active.replaceProducts(List.of(product));

        assertThat(collection.status()).isEqualTo(CollectionStatus.HIDDEN);
        assertThat(collection.revise(
                        collection.slug(),
                        collection.name(),
                        collection.description(),
                        collection.status(),
                        collection.displayOrder()))
                .isSameAs(collection);
        assertThat(collection.replaceProducts(List.of())).isSameAs(collection);
        assertThat(active.version()).isEqualTo(1);
        assertThat(populated.version()).isEqualTo(2);
        assertThat(populated.productIds()).containsExactly(product);
    }

    @Test
    void rejectsDuplicateMembershipAndArchivedLikeInvalidContent() {
        var product = new ProductId(new UUID(0, 2));
        var collection = EditorialCollection.create(
                new CollectionId(new UUID(0, 1)),
                new CollectionSlug("week-selection"),
                "Выбор недели",
                "Редакционная подборка",
                10);

        assertThatThrownBy(() -> collection.replaceProducts(List.of(product, product)))
                .isInstanceOfSatisfying(
                        CollectionInvariantViolation.class,
                        violation ->
                                assertThat(violation.invariant()).isEqualTo(CollectionInvariant.DUPLICATE_PRODUCT));
        assertThatThrownBy(() ->
                        collection.revise(collection.slug(), " ", collection.description(), collection.status(), 0))
                .isInstanceOf(CollectionInvariantViolation.class);
    }
}
