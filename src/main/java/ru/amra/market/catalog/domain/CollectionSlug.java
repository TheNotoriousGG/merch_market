package ru.amra.market.catalog.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalized stable URL slug for an editorial collection. */
public record CollectionSlug(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public CollectionSlug {
        if (value == null) {
            throw invalid();
        }
        value = value.strip().toLowerCase(Locale.ROOT);
        if (value.length() > 120 || !FORMAT.matcher(value).matches()) {
            throw invalid();
        }
    }

    private static CollectionInvariantViolation invalid() {
        return new CollectionInvariantViolation(
                CollectionInvariant.INVALID_SLUG, "Collection slug must use lower kebab-case ASCII");
    }
}
