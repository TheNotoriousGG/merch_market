package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductVariantTest {

    @Test
    void normalizesSkuAndBuildsOrderIndependentCombination() {
        var variant = ProductFixtures.variant(1, " amr-ts01-blk-m ", "graphite", "m");

        assertThat(variant.sku().value()).isEqualTo("AMR-TS01-BLK-M");
        assertThat(variant.attributes()).extracting(AttributeValue::code).containsExactly("color", "size");
        assertThat(variant.definingCombination()).isEqualTo("color=GRAPHITE|size=M");
    }

    @Test
    void skuRemainsUnchangedWhenVariantIsArchived() {
        var variant = ProductFixtures.variant(1, "AMR-TS01-BLK-M", "BLACK", "M");

        var archived = variant.archive();

        assertThat(archived.sku()).isEqualTo(variant.sku());
        assertThat(archived.status()).isEqualTo(VariantStatus.ARCHIVED);
        assertThat(archived.version()).isEqualTo(1);
        assertThat(archived.archive()).isSameAs(archived);
    }

    @Test
    void rejectsDuplicateAttributeCodesAndMissingDefiningAttributes() {
        var color = new AttributeValue("color", "Цвет", AttributeType.COLOR, "BLACK", true, 0);
        var duplicate = new AttributeValue("color", "Другой цвет", AttributeType.COLOR, "WHITE", true, 1);

        assertInvariant(
                ProductInvariant.INVALID_ATTRIBUTE,
                () -> ProductVariant.create(
                        ProductFixtures.variantId(1), new Sku("AMR-1"), "Black", 0, List.of(color, duplicate)));
        assertInvariant(
                ProductInvariant.INVALID_ATTRIBUTE,
                () -> ProductVariant.create(
                        ProductFixtures.variantId(1),
                        new Sku("AMR-1"),
                        "Cotton",
                        0,
                        List.of(ProductFixtures.material())));
    }

    private static void assertInvariant(ProductInvariant expected, Runnable command) {
        assertThatThrownBy(command::run)
                .isInstanceOfSatisfying(
                        ProductInvariantViolation.class,
                        violation -> assertThat(violation.invariant()).isEqualTo(expected));
    }
}
