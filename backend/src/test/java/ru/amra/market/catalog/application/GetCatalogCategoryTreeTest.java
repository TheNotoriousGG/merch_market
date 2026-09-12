package ru.amra.market.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.amra.market.catalog.application.port.VisibleCategoryRecord;

class GetCatalogCategoryTreeTest {

    private static final UUID ACCESSORIES_ID = UUID.fromString("01991a80-0000-7000-8000-000000000001");
    private static final UUID CLOTHES_ID = UUID.fromString("01991a80-0000-7000-8000-000000000002");
    private static final UUID TROUSERS_ID = UUID.fromString("01991a80-0000-7000-8000-000000000003");

    @Test
    void buildsNestedNavigationInStableOrderRegardlessOfDatabaseRowOrder() {
        var source = List.of(
                category(TROUSERS_ID, CLOTHES_ID, "trousers", "Брюки", 0),
                category(CLOTHES_ID, null, "clothes", "Одежда", 20),
                category(ACCESSORIES_ID, null, "accessories", "Аксессуары", 10));

        var result = new GetCatalogCategoryTree(() -> source).execute();

        assertThat(result.categories())
                .extracting(CatalogCategoryTree.Node::slug)
                .containsExactly("accessories", "clothes");
        assertThat(result.categories().get(1).children())
                .extracting(CatalogCategoryTree.Node::slug)
                .containsExactly("trousers");
        assertThat(result.etag()).matches("\"[0-9a-f]{64}\"");
    }

    @Test
    void etagRepresentsPublicTreeContentAndNotSourceRowOrder() {
        var clothes = category(CLOTHES_ID, null, "clothes", "Одежда", 20);
        var accessories = category(ACCESSORIES_ID, null, "accessories", "Аксессуары", 10);
        var first = new GetCatalogCategoryTree(() -> List.of(clothes, accessories)).execute();
        var reorderedSource = new GetCatalogCategoryTree(() -> List.of(accessories, clothes)).execute();
        var renamed = new GetCatalogCategoryTree(
                        () -> List.of(accessories, category(CLOTHES_ID, null, "clothes", "Новая одежда", 20)))
                .execute();

        assertThat(reorderedSource.etag()).isEqualTo(first.etag());
        assertThat(renamed.etag()).isNotEqualTo(first.etag());
    }

    @Test
    void returnsImmutableEmptyTreeWithStableEtag() {
        var result = new GetCatalogCategoryTree(List::of).execute();

        assertThat(result.categories()).isEmpty();
        assertThat(result.etag()).matches("\"[0-9a-f]{64}\"");
    }

    private static VisibleCategoryRecord category(
            UUID id, @org.jspecify.annotations.Nullable UUID parentId, String slug, String name, int order) {
        return new VisibleCategoryRecord(id, parentId, slug, name, order);
    }
}
