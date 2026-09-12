package ru.amra.market.catalog.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.VisibleCategoryReader;
import ru.amra.market.catalog.application.port.VisibleCategoryRecord;

/** Builds the deterministic, cache-addressable category tree used by storefront navigation. */
@Service
public class GetCatalogCategoryTree {

    private static final Comparator<VisibleCategoryRecord> NAVIGATION_ORDER =
            Comparator.comparingInt(VisibleCategoryRecord::displayOrder).thenComparing(VisibleCategoryRecord::id);

    private final VisibleCategoryReader reader;

    public GetCatalogCategoryTree(VisibleCategoryReader reader) {
        this.reader = reader;
    }

    /** Returns the complete visible tree and a strong ETag of its public representation. */
    @Transactional(readOnly = true)
    public CatalogCategoryTree execute() {
        var flat = reader.findAllReachable();
        var roots = new ArrayList<VisibleCategoryRecord>();
        var childrenByParent = new HashMap<UUID, List<VisibleCategoryRecord>>();
        for (var category : flat) {
            if (category.parentId() == null) {
                roots.add(category);
            } else {
                childrenByParent
                        .computeIfAbsent(category.parentId(), ignored -> new ArrayList<>())
                        .add(category);
            }
        }
        roots.sort(NAVIGATION_ORDER);
        childrenByParent.values().forEach(children -> children.sort(NAVIGATION_ORDER));

        var categories =
                roots.stream().map(root -> toNode(root, childrenByParent)).toList();
        return new CatalogCategoryTree(categories, etag(categories));
    }

    private static CatalogCategoryTree.Node toNode(
            VisibleCategoryRecord category, Map<UUID, List<VisibleCategoryRecord>> childrenByParent) {
        var children = childrenByParent.getOrDefault(category.id(), List.of()).stream()
                .map(child -> toNode(child, childrenByParent))
                .toList();
        return new CatalogCategoryTree.Node(
                category.id(), category.slug(), category.name(), category.displayOrder(), children);
    }

    private static String etag(List<CatalogCategoryTree.Node> categories) {
        var hash = new RepresentationHasher();
        update(hash, categories);
        return hash.etag();
    }

    private static void update(RepresentationHasher hash, List<CatalogCategoryTree.Node> nodes) {
        hash.add(nodes.size());
        for (var node : nodes) {
            hash.add(node.id()).add(node.slug()).add(node.name()).add(node.displayOrder());
            update(hash, node.children());
        }
    }
}
