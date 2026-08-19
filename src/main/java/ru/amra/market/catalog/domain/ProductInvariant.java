package ru.amra.market.catalog.domain;

/** Stable identifiers for product aggregate invariant violations. */
public enum ProductInvariant {
    INVALID_ID,
    INVALID_SLUG,
    INVALID_CONTENT,
    INVALID_ASSIGNMENT,
    INVALID_ATTRIBUTE,
    INVALID_SKU,
    DUPLICATE_SKU,
    DUPLICATE_VARIANT_COMBINATION,
    INVALID_MEDIA,
    MULTIPLE_PRIMARY_MEDIA,
    INVALID_LIFECYCLE_TRANSITION,
    PRODUCT_NOT_PUBLISHABLE,
    SLUG_REUSE,
    SLUG_NAMESPACE_CONFLICT,
    INVALID_VERSION
}
