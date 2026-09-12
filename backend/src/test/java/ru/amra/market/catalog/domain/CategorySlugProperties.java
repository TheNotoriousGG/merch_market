package ru.amra.market.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CategorySlugProperties {

    @Property
    void canonicalizationIsIdempotent(@ForAll("validMixedCaseSlugs") String candidate) {
        var first = new CategorySlug("  " + candidate + "  ");
        var second = new CategorySlug(first.value());

        assertThat(first).isEqualTo(second);
        assertThat(first.value()).isEqualTo(candidate.toLowerCase(Locale.ROOT));
    }

    @Provide
    Arbitrary<String> validMixedCaseSlugs() {
        var segment = Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .ofMinLength(1)
                .ofMaxLength(12);
        return segment.list().ofMinSize(1).ofMaxSize(4).map(parts -> String.join("-", parts));
    }
}
