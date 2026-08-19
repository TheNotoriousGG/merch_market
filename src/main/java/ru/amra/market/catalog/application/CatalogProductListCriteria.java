package ru.amra.market.catalog.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Validated and normalized input for one public catalog page. */
public record CatalogProductListCriteria(
        int page,
        int size,
        @Nullable String category,
        @Nullable String collection,
        @Nullable String search,
        boolean onlyNew,
        List<String> sizeValues,
        List<String> colorValues,
        CatalogProductSort sort) {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern FILTER_VALUE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,31}");

    public CatalogProductListCriteria {
        if (page < 0) {
            throw invalid("Page must not be negative");
        }
        if (size < 1 || size > 60) {
            throw invalid("Page size must be between 1 and 60");
        }
        category = slug(category, "category");
        collection = slug(collection, "collection");
        search = search(search);
        sizeValues = filterValues(sizeValues, "size");
        colorValues = filterValues(colorValues, "color");
        if (sort == null) {
            throw invalid("Catalog sort must not be null");
        }
    }

    private static @Nullable String slug(@Nullable String candidate, String field) {
        if (candidate == null) {
            return null;
        }
        var normalized = candidate.strip();
        if (normalized.length() > 120 || !SLUG.matcher(normalized).matches()) {
            throw invalid("Catalog " + field + " slug is invalid");
        }
        return normalized;
    }

    private static @Nullable String search(@Nullable String candidate) {
        if (candidate == null) {
            return null;
        }
        var normalized = candidate.strip();
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw invalid("Catalog search must contain between 2 and 100 characters after trimming");
        }
        return normalized;
    }

    private static List<String> filterValues(List<String> candidates, String field) {
        if (candidates == null) {
            throw invalid("Catalog " + field + " filters must not be null");
        }
        var normalized = new LinkedHashSet<String>();
        for (var candidate : candidates) {
            if (candidate == null || !FILTER_VALUE.matcher(candidate).matches()) {
                throw invalid("Catalog " + field + " filter is invalid");
            }
            normalized.add(candidate);
        }
        if (normalized.size() > 20) {
            throw invalid("Catalog " + field + " filters cannot contain more than 20 values");
        }
        return List.copyOf(normalized);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
