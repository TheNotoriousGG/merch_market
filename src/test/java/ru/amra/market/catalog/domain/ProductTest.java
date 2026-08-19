package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Instant PUBLICATION_TIME = Instant.parse("2026-08-19T10:00:00Z");

    @Test
    void createsDraftWithoutInventingPublicationTime() {
        var product = ProductFixtures.draft(1, "futbolka-seriya-01");

        assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.publishedAt()).isEmpty();
        assertThat(product.version()).isZero();
    }

    @Test
    void returnsCompletePublicationValidationReport() {
        var product = ProductFixtures.draft(1, "futbolka-seriya-01");

        assertThat(product.publicationViolations(Set.of()))
                .containsExactly(
                        "Primary category must be active",
                        "At least one active variant is required",
                        "Exactly one primary image with alt text is required");
        assertThatThrownBy(() -> product.publish(Set.of(), PUBLICATION_TIME))
                .isInstanceOfSatisfying(ProductInvariantViolation.class, violation -> {
                    assertThat(violation.invariant()).isEqualTo(ProductInvariant.PRODUCT_NOT_PUBLISHABLE);
                    assertThat(violation.violations()).hasSize(3);
                });
    }

    @Test
    void publishesCompleteDraftAndArchivesOnlyFromActiveState() {
        var draft = ProductFixtures.completeDraft(1, "futbolka-seriya-01", "AMR-TS01-BLK-M");

        var active = draft.publish(Set.of(draft.primaryCategoryId()), PUBLICATION_TIME);
        var archived = active.archive();

        assertThat(active.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(active.publishedAt()).contains(PUBLICATION_TIME);
        assertThat(active.version()).isEqualTo(3);
        assertThat(archived.status()).isEqualTo(ProductStatus.ARCHIVED);
        assertThatThrownBy(archived::archive)
                .isInstanceOfSatisfying(
                        ProductInvariantViolation.class,
                        violation -> assertThat(violation.invariant())
                                .isEqualTo(ProductInvariant.INVALID_LIFECYCLE_TRANSITION));
        assertThatThrownBy(() -> draft.archive()).isInstanceOf(ProductInvariantViolation.class);
    }

    @Test
    void rejectsDuplicateSkuAndDefiningCombinationInsideProduct() {
        var first = ProductFixtures.variant(1, "AMR-TS01-BLK-M", "BLACK", "M");
        var sameSku = ProductFixtures.variant(2, "AMR-TS01-BLK-M", "WHITE", "L");
        var sameCombination = ProductFixtures.variant(3, "AMR-TS01-ALT", "BLACK", "M");
        var product = ProductFixtures.draft(1, "futbolka").addVariant(first);

        assertInvariant(ProductInvariant.DUPLICATE_SKU, () -> product.addVariant(sameSku));
        assertInvariant(ProductInvariant.DUPLICATE_VARIANT_COMBINATION, () -> product.addVariant(sameCombination));
    }

    @Test
    void archivesVariantInDraftButRetainsLastActiveVariantInPublishedProduct() {
        var draft = ProductFixtures.completeDraft(1, "futbolka", "AMR-TS01-BLK-M");
        var variantId = draft.variants().getFirst().id();

        var withoutActiveVariant = draft.archiveVariant(variantId);

        assertThat(withoutActiveVariant.variants().getFirst().status()).isEqualTo(VariantStatus.ARCHIVED);
        var active = draft.publish(Set.of(draft.primaryCategoryId()), PUBLICATION_TIME);
        assertInvariant(ProductInvariant.PRODUCT_NOT_PUBLISHABLE, () -> active.archiveVariant(variantId));
        assertInvariant(ProductInvariant.INVALID_ID, () -> draft.archiveVariant(ProductFixtures.variantId(999)));
    }

    @Test
    void rejectsSecondPrimaryMediaAndForeignVariantMedia() {
        var variant = ProductFixtures.variant(1, "AMR-TS01-BLK-M", "BLACK", "M");
        var product = ProductFixtures.draft(1, "futbolka")
                .addVariant(variant)
                .addMedia(ProductFixtures.primaryMedia(1, variant.id()));

        assertInvariant(
                ProductInvariant.MULTIPLE_PRIMARY_MEDIA, () -> product.addMedia(ProductFixtures.primaryMedia(2, null)));
        assertInvariant(
                ProductInvariant.INVALID_MEDIA,
                () -> ProductFixtures.draft(2, "hudi")
                        .addMedia(ProductFixtures.primaryMedia(3, ProductFixtures.variantId(99))));
    }

    @Test
    void keepsDirectSlugHistoryAndForbidsHistoricalReuse() {
        var original = ProductFixtures.draft(1, "old-slug");

        var renamed = original.changeSlug(new ProductSlug("middle-slug")).changeSlug(new ProductSlug("canonical-slug"));

        assertThat(renamed.aliases())
                .containsExactlyInAnyOrder(new ProductSlug("old-slug"), new ProductSlug("middle-slug"));
        assertInvariant(ProductInvariant.SLUG_REUSE, () -> renamed.changeSlug(new ProductSlug("old-slug")));
    }

    @Test
    void revisesRootVariantAndMediaAsSingleAggregateMutations() {
        var original = ProductFixtures.completeDraft(1, "futbolka", "AMR-TS01-BLK-M");
        var category = original.primaryCategoryId();
        var revised = original.revise(
                new ProductSlug("futbolka-new"),
                new ProductContent("Новая футболка", "Коротко", "Подробно"),
                category,
                Set.of(category),
                Set.of(),
                original.characteristics());
        var variant = revised.variants().getFirst();
        var withVariant =
                revised.updateVariant(variant.revise("Графит / M", variant.status(), 3, variant.attributes()));
        var media = withVariant.media().getFirst();
        var withMedia = withVariant.updateMedia(media.revise(null, "Новая футболка", 2, true));

        assertThat(revised.version()).isEqualTo(original.version() + 1);
        assertThat(revised.aliases()).contains(new ProductSlug("futbolka"));
        assertThat(withVariant.variants().getFirst().version()).isEqualTo(1);
        assertThat(withMedia.media().getFirst().version()).isEqualTo(1);
    }

    @Test
    void requiresPrimaryCategoryInAssignmentsAndUniqueCharacteristics() {
        var primary = ProductFixtures.categoryId(1);
        var duplicateMaterial = new AttributeValue("material", "Состав", AttributeType.TEXT, "Лён", false, 1);

        assertInvariant(
                ProductInvariant.INVALID_ASSIGNMENT,
                () -> Product.create(
                        ProductFixtures.productId(1),
                        new ProductSlug("futbolka"),
                        new ProductContent("Футболка", "Коротко", "Подробно"),
                        primary,
                        Set.of(ProductFixtures.categoryId(2)),
                        Set.of(),
                        List.of()));
        assertInvariant(
                ProductInvariant.INVALID_ATTRIBUTE,
                () -> Product.create(
                        ProductFixtures.productId(1),
                        new ProductSlug("futbolka"),
                        new ProductContent("Футболка", "Коротко", "Подробно"),
                        primary,
                        Set.of(primary),
                        Set.of(),
                        List.of(ProductFixtures.material(), duplicateMaterial)));
    }

    private static void assertInvariant(ProductInvariant expected, Runnable command) {
        assertThatThrownBy(command::run)
                .isInstanceOfSatisfying(
                        ProductInvariantViolation.class,
                        violation -> assertThat(violation.invariant()).isEqualTo(expected));
    }
}
