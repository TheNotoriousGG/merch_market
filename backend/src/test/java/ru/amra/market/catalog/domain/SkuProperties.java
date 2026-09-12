package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SkuProperties {

    @Property
    void normalizationIsIdempotent(@ForAll("validMixedCaseSkus") String candidate) {
        var normalized = new Sku(" " + candidate + " ");

        assertThat(new Sku(normalized.value())).isEqualTo(normalized);
        assertThat(normalized.value()).isEqualTo(candidate.toUpperCase(Locale.ROOT));
    }

    @Provide
    Arbitrary<String> validMixedCaseSkus() {
        var segment = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(10);
        return segment.list().ofMinSize(1).ofMaxSize(5).map(parts -> String.join("-", parts));
    }
}
