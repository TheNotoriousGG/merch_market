package ru.amra.market.catalog.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Immutable normalized business SKU. */
public record Sku(String value) implements Comparable<Sku> {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9]+(?:-[A-Z0-9]+)*");

    public Sku {
        if (value == null) {
            throw invalid();
        }
        value = value.strip().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 64 || !FORMAT.matcher(value).matches()) {
            throw invalid();
        }
    }

    @Override
    public int compareTo(Sku other) {
        return value.compareTo(other.value);
    }

    private static ProductInvariantViolation invalid() {
        return new ProductInvariantViolation(
                ProductInvariant.INVALID_SKU, "SKU must be 1-64 uppercase ASCII groups separated by single hyphens");
    }
}
