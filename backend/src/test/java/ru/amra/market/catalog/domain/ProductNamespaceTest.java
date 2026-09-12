package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductNamespaceTest {

    @Test
    void resolvesHistoricalAliasDirectlyToCurrentCanonicalSlug() {
        var product = ProductFixtures.draft(1, "old-slug")
                .changeSlug(new ProductSlug("middle-slug"))
                .changeSlug(new ProductSlug("canonical-slug"));
        var index = new ProductSlugIndex(List.of(product));

        var resolution = index.resolve(new ProductSlug("old-slug")).orElseThrow();

        assertThat(resolution.productId()).isEqualTo(product.id());
        assertThat(resolution.canonicalSlug()).isEqualTo(new ProductSlug("canonical-slug"));
        assertThat(resolution.alias()).isTrue();
    }

    @Test
    void rejectsCanonicalOrAliasReuseAcrossProducts() {
        var renamed = ProductFixtures.draft(1, "reserved-slug").changeSlug(new ProductSlug("first-product"));
        var conflicting = ProductFixtures.draft(2, "reserved-slug");

        assertInvariant(() -> new ProductSlugIndex(List.of(renamed, conflicting)));
    }

    @Test
    void skuRemainsGloballyReservedAcrossProducts() {
        var first = ProductFixtures.completeDraft(1, "first", "AMR-GLOBAL-1");
        var second = ProductFixtures.completeDraft(2, "second", "AMR-GLOBAL-1");

        assertThatThrownBy(() -> new ProductSkuIndex(List.of(first, second)))
                .isInstanceOfSatisfying(
                        ProductInvariantViolation.class,
                        violation -> assertThat(violation.invariant()).isEqualTo(ProductInvariant.DUPLICATE_SKU));
    }

    private static void assertInvariant(Runnable command) {
        assertThatThrownBy(command::run)
                .isInstanceOfSatisfying(
                        ProductInvariantViolation.class,
                        violation ->
                                assertThat(violation.invariant()).isEqualTo(ProductInvariant.SLUG_NAMESPACE_CONFLICT));
    }
}
