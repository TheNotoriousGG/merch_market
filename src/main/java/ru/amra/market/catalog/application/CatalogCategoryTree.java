package ru.amra.market.catalog.application;

import java.util.List;
import java.util.UUID;

/** Immutable application result for storefront category navigation. */
public record CatalogCategoryTree(List<Node> categories, String etag) {

    public CatalogCategoryTree {
        categories = List.copyOf(categories);
    }

    /** One recursively nested visible navigation node. */
    public record Node(UUID id, String slug, String name, int displayOrder, List<Node> children) {

        public Node {
            children = List.copyOf(children);
        }
    }
}
