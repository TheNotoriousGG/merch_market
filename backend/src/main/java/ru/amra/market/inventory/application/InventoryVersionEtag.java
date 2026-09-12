package ru.amra.market.inventory.application;

import java.util.regex.Pattern;

/** Strong ETag codec for exact inventory balance representations. */
public final class InventoryVersionEtag {
    private static final Pattern FORMAT = Pattern.compile("\"v([0-9]+)\"");

    private InventoryVersionEtag() {}

    /** Encodes a non-negative optimistic version. */
    public static String format(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("Inventory version must not be negative");
        }
        return "\"v" + version + "\"";
    }

    /** Parses a strong inventory ETag. */
    public static long parse(String etag) {
        var matcher = FORMAT.matcher(etag);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Inventory ETag is invalid");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Inventory ETag version is invalid", exception);
        }
    }
}
