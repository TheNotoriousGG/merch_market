package ru.amra.market.catalog.domain;

/** Validated customer-visible product copy. */
public record ProductContent(String name, String shortDescription, String description) {

    public ProductContent {
        name = required(name, 200, "name");
        shortDescription = required(shortDescription, 500, "short description");
        description = required(description, 10_000, "description");
    }

    private static String required(String value, int maximumLength, String field) {
        if (value == null) {
            throw invalid(field, maximumLength);
        }
        var normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw invalid(field, maximumLength);
        }
        return normalized;
    }

    private static ProductInvariantViolation invalid(String field, int maximumLength) {
        return new ProductInvariantViolation(
                ProductInvariant.INVALID_CONTENT,
                "Product " + field + " must contain 1-" + maximumLength + " characters");
    }
}
