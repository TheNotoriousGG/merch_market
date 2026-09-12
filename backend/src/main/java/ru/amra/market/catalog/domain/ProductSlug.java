package ru.amra.market.catalog.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical or historical URL-safe product slug. */
public record ProductSlug(String value) implements Comparable<ProductSlug> {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public ProductSlug {
        if (value == null) {
            throw invalid();
        }
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 120 || !FORMAT.matcher(value).matches()) {
            throw invalid();
        }
    }

    @Override
    public int compareTo(ProductSlug other) {
        return value.compareTo(other.value);
    }

    private static ProductInvariantViolation invalid() {
        return new ProductInvariantViolation(
                ProductInvariant.INVALID_SLUG,
                "Product slug must be 1-120 lowercase ASCII characters separated by single hyphens");
    }
}
