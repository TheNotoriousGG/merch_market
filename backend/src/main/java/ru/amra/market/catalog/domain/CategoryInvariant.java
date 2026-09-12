package ru.amra.market.catalog.domain;

/** Stable identifiers for category invariant violations. */
public enum CategoryInvariant {
    INVALID_ID,
    INVALID_SLUG,
    INVALID_NAME,
    INVALID_DISPLAY_ORDER,
    INVALID_VERSION,
    SELF_PARENT,
    ORPHAN_PARENT,
    CATEGORY_CYCLE,
    CATEGORY_DEPTH_EXCEEDED,
    DUPLICATE_SIBLING_SLUG
}
