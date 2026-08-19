package ru.amra.market.catalog.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalized URL-safe category slug. */
public record CategorySlug(String value) {

    private static final int MAX_LENGTH = 120;
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** Creates a lower-case canonical slug from an already transliterated value. */
    public CategorySlug {
        if (value == null) {
            throw invalid("Category slug must not be null");
        }
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()
                || value.length() > MAX_LENGTH
                || !FORMAT.matcher(value).matches()) {
            throw invalid("Category slug must be 1-120 lowercase ASCII characters separated by single hyphens");
        }
    }

    private static CategoryInvariantViolation invalid(String message) {
        return new CategoryInvariantViolation(CategoryInvariant.INVALID_SLUG, message);
    }

    @Override
    public String toString() {
        return value;
    }
}
