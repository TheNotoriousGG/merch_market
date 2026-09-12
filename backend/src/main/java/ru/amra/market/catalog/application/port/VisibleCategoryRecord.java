package ru.amra.market.catalog.application.port;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Flat persistence projection of a category reachable through the visible navigation tree. */
public record VisibleCategoryRecord(UUID id, @Nullable UUID parentId, String slug, String name, int displayOrder) {}
