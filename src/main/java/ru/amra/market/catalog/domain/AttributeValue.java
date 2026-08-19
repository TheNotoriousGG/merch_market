package ru.amra.market.catalog.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validated display/filter value attached to a product or variant. */
public record AttributeValue(
        String code, String displayName, AttributeType type, String value, boolean variantDefining, int displayOrder) {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern ENUM_VALUE = Pattern.compile("[A-Z0-9]+(?:[_-][A-Z0-9]+)*");
    private static final Pattern DIMENSION = Pattern.compile("[0-9]+(?:[.,][0-9]+)? ?(?:mm|cm|m)");

    public AttributeValue {
        code = normalizeCode(code);
        displayName = required(displayName, 120, "display name");
        if (type == null) {
            throw invalid("Attribute type must not be null");
        }
        value = normalizeValue(type, value);
        if (displayOrder < 0) {
            throw invalid("Attribute display order must not be negative");
        }
    }

    /** Stable identity fragment used to compare variant-defining combinations. */
    public String combinationPart() {
        return code + '=' + value;
    }

    private static String normalizeCode(String candidate) {
        var normalized = required(candidate, 64, "code").toLowerCase(Locale.ROOT);
        if (!CODE.matcher(normalized).matches()) {
            throw invalid("Attribute code must use lower snake_case ASCII");
        }
        return normalized;
    }

    private static String normalizeValue(AttributeType type, String candidate) {
        var normalized = required(candidate, 300, "value");
        if (type == AttributeType.COLOR || type == AttributeType.SIZE) {
            normalized = normalized.toUpperCase(Locale.ROOT);
            if (!ENUM_VALUE.matcher(normalized).matches()) {
                throw invalid("Color and size values must use stable uppercase codes");
            }
        }
        if (type == AttributeType.DIMENSION) {
            normalized = normalized.toLowerCase(Locale.ROOT).replace(',', '.');
            if (!DIMENSION.matcher(normalized).matches()) {
                throw invalid("Dimension value must contain a number and mm, cm, or m unit");
            }
        }
        return normalized;
    }

    private static String required(String candidate, int maximumLength, String field) {
        if (candidate == null) {
            throw invalid("Attribute " + field + " must not be null");
        }
        var normalized = candidate.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw invalid("Attribute " + field + " has an invalid length");
        }
        return normalized;
    }

    private static ProductInvariantViolation invalid(String message) {
        return new ProductInvariantViolation(ProductInvariant.INVALID_ATTRIBUTE, message);
    }
}
