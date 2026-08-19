package ru.amra.market.catalog.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.CatalogIdGenerator;
import ru.amra.market.catalog.application.port.CatalogIdempotencyStore;
import ru.amra.market.catalog.application.port.EditorialCollectionRepository;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.CollectionSlug;
import ru.amra.market.catalog.domain.CollectionStatus;
import ru.amra.market.catalog.domain.EditorialCollection;
import ru.amra.market.catalog.domain.ProductId;

/** Transactional administrative facade for editorial collections and membership order. */
@Service
public class ManageCatalogCollections {

    private static final String CREATE_COLLECTION = "CREATE_COLLECTION";
    private static final String REPLACE_COLLECTION_PRODUCTS = "REPLACE_COLLECTION_PRODUCTS";

    private final EditorialCollectionRepository collections;
    private final CatalogIdGenerator ids;
    private final CatalogIdempotencyStore idempotency;

    public ManageCatalogCollections(
            EditorialCollectionRepository collections, CatalogIdGenerator ids, CatalogIdempotencyStore idempotency) {
        this.collections = collections;
        this.ids = ids;
        this.idempotency = idempotency;
    }

    /** Creates a hidden collection or replays the identical caller-scoped command. */
    @Transactional
    public AdminCollectionView create(CreateCommand command) {
        var replay = idempotency.claim(
                required(command.actorScope(), "actor scope"),
                required(command.idempotencyKey(), "idempotency key"),
                CREATE_COLLECTION,
                fingerprint(command));
        if (replay.isPresent()) {
            return get(new CollectionId(replay.orElseThrow()));
        }
        var saved = collections.save(EditorialCollection.create(
                new CollectionId(ids.next()),
                command.slug(),
                command.name(),
                command.description(),
                command.displayOrder()));
        idempotency.complete(
                command.actorScope(), command.idempotencyKey(), saved.id().value());
        return view(saved);
    }

    /** Reads one administrative collection. */
    @Transactional(readOnly = true)
    public AdminCollectionView get(CollectionId id) {
        return view(load(id));
    }

    /** Applies an optimistic partial metadata update. */
    @Transactional
    public AdminCollectionView update(UpdateCommand command) {
        var current = loadExpected(command.collectionId(), command.expectedVersion());
        return view(collections.save(current.revise(
                command.slug() == null ? current.slug() : command.slug(),
                command.name() == null ? current.name() : command.name(),
                command.description() == null ? current.description() : command.description(),
                command.status() == null ? current.status() : command.status(),
                command.displayOrder() == null ? current.displayOrder() : command.displayOrder())));
    }

    /** Atomically replaces ordered membership and deduplicates repeated identifiers. */
    @Transactional
    public AdminCollectionView replaceProducts(ReplaceProductsCommand command) {
        var productIds = distinct(command.productIds());
        var replay = idempotency.claim(
                required(command.actorScope(), "actor scope"),
                required(command.idempotencyKey(), "idempotency key"),
                REPLACE_COLLECTION_PRODUCTS,
                fingerprint(command, productIds));
        if (replay.isPresent()) {
            return get(new CollectionId(replay.orElseThrow()));
        }
        var current = loadExpected(command.collectionId(), command.expectedVersion());
        var saved = collections.save(current.replaceProducts(productIds));
        idempotency.complete(
                command.actorScope(), command.idempotencyKey(), saved.id().value());
        return view(saved);
    }

    private EditorialCollection load(CollectionId id) {
        return collections.findById(id).orElseThrow(CatalogCollectionNotFoundException::new);
    }

    private EditorialCollection loadExpected(CollectionId id, long expectedVersion) {
        var collection = load(id);
        if (collection.version() != expectedVersion) {
            throw new StaleCatalogVersionException();
        }
        return collection;
    }

    private static AdminCollectionView view(EditorialCollection collection) {
        return new AdminCollectionView(collection, CatalogVersionEtag.format(collection.version()));
    }

    private static List<ProductId> distinct(List<ProductId> productIds) {
        return new ArrayList<>(new LinkedHashSet<>(productIds));
    }

    private static String fingerprint(CreateCommand command) {
        return new RepresentationHasher()
                .add(command.slug().value())
                .add(command.name())
                .add(command.description())
                .add(command.displayOrder())
                .hexDigest();
    }

    private static String fingerprint(ReplaceProductsCommand command, List<ProductId> products) {
        var hash = new RepresentationHasher()
                .add(command.collectionId().value())
                .add(command.expectedVersion())
                .add(products.size());
        products.forEach(product -> hash.add(product.value()));
        return hash.hexDigest();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Catalog " + field + " must not be blank");
        }
        return value;
    }

    public record CreateCommand(
            CollectionSlug slug,
            String name,
            String description,
            int displayOrder,
            String actorScope,
            String idempotencyKey) {}

    public record UpdateCommand(
            CollectionId collectionId,
            long expectedVersion,
            @Nullable CollectionSlug slug,
            @Nullable String name,
            @Nullable String description,
            @Nullable CollectionStatus status,
            @Nullable Integer displayOrder) {}

    public record ReplaceProductsCommand(
            CollectionId collectionId,
            long expectedVersion,
            List<ProductId> productIds,
            String actorScope,
            String idempotencyKey) {}
}
