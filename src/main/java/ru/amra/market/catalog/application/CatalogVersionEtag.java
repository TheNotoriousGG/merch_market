package ru.amra.market.catalog.application;

import java.util.regex.Pattern;

/** Strong ETag codec used by administrative optimistic concurrency commands. */
public final class CatalogVersionEtag {

    private static final Pattern FORMAT = Pattern.compile("\"v([0-9]+)\"");

    private CatalogVersionEtag() {}

    public static String format(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("Catalog version must not be negative");
        }
        return "\"v" + version + "\"";
    }

    public static long parse(String etag) {
        var matcher = FORMAT.matcher(etag);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Catalog ETag is invalid");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Catalog ETag version is invalid", exception);
        }
    }
}
