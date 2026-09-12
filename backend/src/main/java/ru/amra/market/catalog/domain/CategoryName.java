package ru.amra.market.catalog.domain;

/** Customer-visible category name. */
public record CategoryName(String value) {

    private static final int MAX_LENGTH = 160;

    /** Creates a trimmed nonblank category name. */
    public CategoryName {
        if (value == null) {
            throw invalid("Category name must not be null");
        }
        value = value.strip();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw invalid("Category name must contain 1-160 characters");
        }
    }

    private static CategoryInvariantViolation invalid(String message) {
        return new CategoryInvariantViolation(CategoryInvariant.INVALID_NAME, message);
    }

    @Override
    public String toString() {
        return value;
    }
}
