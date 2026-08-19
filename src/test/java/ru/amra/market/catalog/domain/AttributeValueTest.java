package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AttributeValueTest {

    @Test
    void preservesStableCodeLocalizedLabelAndOptionalColorPresentation() {
        var color = new AttributeValue("color", "Цвет", AttributeType.COLOR, "graphite", "Графит", "#4A4A4A", true, 0);
        var dimension =
                new AttributeValue("length", "Длина", AttributeType.DIMENSION, "12,5 CM", "12,5 см", null, false, 1);

        assertThat(color.value()).isEqualTo("GRAPHITE");
        assertThat(color.label()).isEqualTo("Графит");
        assertThat(color.colorHex()).isEqualTo("#4A4A4A");
        assertThat(dimension.value()).isEqualTo("12.5 cm");
    }

    @Test
    void rejectsColorPresentationOnWrongTypeAndMalformedValues() {
        assertThatThrownBy(() -> new AttributeValue("size", "Размер", AttributeType.SIZE, "M", "M", "#FFFFFF", true, 0))
                .isInstanceOf(ProductInvariantViolation.class);
        assertThatThrownBy(() ->
                        new AttributeValue("color", "Цвет", AttributeType.COLOR, "BLACK", "Чёрный", "black", true, 0))
                .isInstanceOf(ProductInvariantViolation.class);
        assertThatThrownBy(() -> new AttributeValue(
                        "length", "Длина", AttributeType.DIMENSION, "large", "Большой", null, false, 0))
                .isInstanceOf(ProductInvariantViolation.class);
        assertThatThrownBy(() -> new AttributeValue(
                        "material", "Материал", AttributeType.TEXT, "cotton", "Хлопок", null, false, -1))
                .isInstanceOf(ProductInvariantViolation.class);
    }
}
