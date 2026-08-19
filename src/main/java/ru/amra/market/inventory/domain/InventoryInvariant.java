package ru.amra.market.inventory.domain;

/** Stable identifiers for balance and physical movement invariant violations. */
public enum InventoryInvariant {
    INVALID_ID,
    INVALID_QUANTITY,
    NON_POSITIVE_QUANTITY,
    RESERVED_EXCEEDS_ON_HAND,
    INSUFFICIENT_STOCK,
    RESERVED_UNDERFLOW,
    QUANTITY_OVERFLOW,
    INVALID_VERSION,
    VERSION_OVERFLOW,
    INVALID_MOVEMENT,
    INVALID_REASON,
    INVALID_REFERENCE,
    NO_PHYSICAL_CHANGE
}
