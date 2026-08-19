package ru.amra.market.catalog.application.port;

import java.util.List;

/** Read-optimized boundary for the storefront category navigation. */
public interface VisibleCategoryReader {

    /** Loads all visible nodes reachable from visible roots in one bounded operation. */
    List<VisibleCategoryRecord> findAllReachable();
}
