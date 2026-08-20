package ru.amra.market.catalog.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.ActiveCategoryReader;
import ru.amra.market.catalog.application.port.AdminProductReader;
import ru.amra.market.catalog.application.port.CatalogIdGenerator;
import ru.amra.market.catalog.application.port.CatalogIdempotencyStore;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.MediaId;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductContent;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductInvariant;
import ru.amra.market.catalog.domain.ProductInvariantViolation;
import ru.amra.market.catalog.domain.ProductMedia;
import ru.amra.market.catalog.domain.ProductMerchandising;
import ru.amra.market.catalog.domain.ProductPrice;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductStatus;
import ru.amra.market.catalog.domain.ProductVariant;
import ru.amra.market.catalog.domain.Sku;
import ru.amra.market.catalog.domain.VariantId;
import ru.amra.market.catalog.domain.VariantStatus;

/** Transactional administrative facade for product, variant and media aggregates. */
@Service
public class ManageCatalogProducts {

    private static final String CREATE_PRODUCT = "CREATE_PRODUCT";
    private static final String TRANSITION_PRODUCT = "TRANSITION_PRODUCT";
    private static final String CREATE_VARIANT = "CREATE_PRODUCT_VARIANT";
    private static final String CREATE_MEDIA = "CREATE_PRODUCT_MEDIA";

    private final ProductRepository products;
    private final AdminProductReader adminReader;
    private final ActiveCategoryReader activeCategories;
    private final CatalogIdGenerator ids;
    private final CatalogIdempotencyStore idempotency;
    private final Clock clock;
    private final CatalogAuditTrail audit;
    private final MediaUploadService mediaStorage;

    public ManageCatalogProducts(
            ProductRepository products,
            AdminProductReader adminReader,
            ActiveCategoryReader activeCategories,
            CatalogIdGenerator ids,
            CatalogIdempotencyStore idempotency,
            Clock clock,
            CatalogAuditTrail audit,
            MediaUploadService mediaStorage) {
        this.products = products;
        this.adminReader = adminReader;
        this.activeCategories = activeCategories;
        this.ids = ids;
        this.idempotency = idempotency;
        this.clock = clock;
        this.audit = audit;
        this.mediaStorage = mediaStorage;
    }

    /** Creates a draft product or replays the caller's identical create command. */
    @Transactional
    public AdminProductView create(CreateCommand command) {
        var replay = idempotency.claim(
                required(command.actorScope(), "actor scope"),
                required(command.idempotencyKey(), "idempotency key"),
                CREATE_PRODUCT,
                fingerprint(command));
        if (replay.isPresent()) {
            return get(new ProductId(replay.orElseThrow()));
        }
        var product = Product.create(
                new ProductId(ids.next()),
                command.slug(),
                command.content(),
                command.price(),
                command.primaryCategoryId(),
                command.categoryIds(),
                command.collectionIds(),
                command.characteristics());
        var saved = products.save(product);
        audit.record(
                "PRODUCT",
                saved.id().value(),
                "CREATED",
                null,
                saved.version(),
                Map.of("slug", saved.slug().value(), "status", saved.status().name()));
        idempotency.complete(
                command.actorScope(), command.idempotencyKey(), saved.id().value());
        return view(saved);
    }

    /** Reads the complete administrative product representation. */
    @Transactional(readOnly = true)
    public AdminProductView get(ProductId id) {
        return view(load(id));
    }

    /** Applies one partial root-content update guarded by the product ETag. */
    @Transactional
    public AdminProductView update(UpdateCommand command) {
        var current = loadExpected(command.productId(), command.expectedVersion());
        var content = new ProductContent(
                command.name() == null ? current.content().name() : command.name(),
                command.shortDescription() == null ? current.content().shortDescription() : command.shortDescription(),
                command.description() == null ? current.content().description() : command.description());
        var changed = current.revise(
                command.slug() == null ? current.slug() : command.slug(),
                content,
                command.primaryCategoryId() == null ? current.primaryCategoryId() : command.primaryCategoryId(),
                command.categoryIds() == null ? current.categoryIds() : command.categoryIds(),
                command.collectionIds() == null ? current.collectionIds() : command.collectionIds(),
                command.characteristics() == null ? current.characteristics() : command.characteristics(),
                command.merchandising() == null ? current.merchandising() : command.merchandising(),
                command.price() == null ? current.price().orElse(null) : command.price());
        ensureActiveProductRemainsPublishable(changed);
        var saved = products.save(changed);
        audit.record(
                "PRODUCT",
                saved.id().value(),
                "UPDATED",
                current.version(),
                saved.version(),
                Map.of(
                        "statusFrom",
                        current.status().name(),
                        "statusTo",
                        saved.status().name()));
        return view(saved);
    }

    /** Publishes or archives a product once and safely deduplicates transport retries. */
    @Transactional
    public AdminProductView transition(TransitionCommand command) {
        var replay = idempotency.claim(
                required(command.actorScope(), "actor scope"),
                required(command.idempotencyKey(), "idempotency key"),
                TRANSITION_PRODUCT,
                fingerprint(command));
        if (replay.isPresent()) {
            return get(new ProductId(replay.orElseThrow()));
        }
        var current = loadExpected(command.productId(), command.expectedVersion());
        var changed =
                switch (command.transition()) {
                    case PUBLISH ->
                        current.publish(activeCategories.findActive(current.categoryIds()), clock.instant());
                    case ARCHIVE -> current.archive();
                    case RESTORE -> current.restoreAsDraft();
                };
        var saved = products.save(changed);
        audit.record(
                "PRODUCT",
                saved.id().value(),
                switch (command.transition()) { case PUBLISH -> "PUBLISHED"; case ARCHIVE -> "ARCHIVED"; case RESTORE -> "RESTORED"; },
                current.version(),
                saved.version(),
                Map.of(
                        "statusFrom",
                        current.status().name(),
                        "statusTo",
                        saved.status().name()));
        idempotency.complete(
                command.actorScope(), command.idempotencyKey(), saved.id().value());
        return view(saved);
    }

    /** Permanently deletes an archived product and its stored media. */
    @Transactional
    public void delete(ProductId id, long expectedVersion) {
        var current = loadExpected(id, expectedVersion);
        if (current.status() != ProductStatus.ARCHIVED) {
            throw new IllegalArgumentException("Only archived products can be permanently deleted");
        }
        mediaStorage.delete(current.media().stream().map(ProductMedia::objectKey).toList());
        products.delete(id);
        audit.record("PRODUCT", id.value(), "DELETED", current.version(), current.version(), Map.of());
    }

    /** Adds an immutable-SKU variant as an owned product mutation. */
    @Transactional
    public AdminVariantView createVariant(CreateVariantCommand command) {
        var replay = idempotency.claim(
                required(command.actorScope(), "actor scope"),
                required(command.idempotencyKey(), "idempotency key"),
                CREATE_VARIANT,
                fingerprint(command));
        if (replay.isPresent()) {
            var product = load(command.productId());
            return variantView(product, new VariantId(replay.orElseThrow()));
        }
        var current = loadExpected(command.productId(), command.expectedVersion());
        var variant = ProductVariant.create(
                new VariantId(ids.next()),
                command.sku(),
                command.label(),
                command.displayOrder(),
                command.attributes());
        var saved = products.save(current.addVariant(variant));
        var storedVariant = saved.variants().stream()
                .filter(candidate -> candidate.id().equals(variant.id()))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        audit.record(
                "PRODUCT_VARIANT",
                storedVariant.id().value(),
                "CREATED",
                null,
                storedVariant.version(),
                Map.of(
                        "productId", saved.id().value().toString(),
                        "productVersion", Long.toString(saved.version()),
                        "status", storedVariant.status().name()));
        idempotency.complete(
                command.actorScope(), command.idempotencyKey(), variant.id().value());
        return variantView(saved, variant.id());
    }

    /** Updates presentation or archives a variant while preserving its SKU. */
    @Transactional
    public AdminVariantView updateVariant(UpdateVariantCommand command) {
        var current = loadExpected(command.productId(), command.expectedVersion());
        var variant = current.variants().stream()
                .filter(candidate -> candidate.id().equals(command.variantId()))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        var replacement = variant.revise(
                command.label() == null ? variant.label() : command.label(),
                command.status() == null ? variant.status() : command.status(),
                command.displayOrder() == null ? variant.displayOrder() : command.displayOrder(),
                command.attributes() == null ? variant.attributes() : command.attributes());
        var saved = products.save(current.updateVariant(replacement));
        var storedVariant = saved.variants().stream()
                .filter(candidate -> candidate.id().equals(replacement.id()))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        audit.record(
                "PRODUCT_VARIANT",
                storedVariant.id().value(),
                "UPDATED",
                variant.version(),
                storedVariant.version(),
                Map.of(
                        "productId", saved.id().value().toString(),
                        "productVersion", Long.toString(saved.version()),
                        "statusFrom", variant.status().name(),
                        "statusTo", storedVariant.status().name()));
        return variantView(saved, replacement.id());
    }

    /** Attaches immutable storage metadata and editable presentation metadata to a product. */
    @Transactional
    public AdminMediaView createMedia(CreateMediaCommand command) {
        var replay = idempotency.claim(
                required(command.actorScope(), "actor scope"),
                required(command.idempotencyKey(), "idempotency key"),
                CREATE_MEDIA,
                fingerprint(command));
        if (replay.isPresent()) {
            var product = load(command.productId());
            return mediaView(product, new MediaId(replay.orElseThrow()));
        }
        var current = loadExpected(command.productId(), command.expectedVersion());
        var media = ProductMedia.image(
                new MediaId(ids.next()),
                command.variantId(),
                command.objectKey(),
                command.contentType(),
                command.width(),
                command.height(),
                command.alt(),
                command.displayOrder(),
                command.primary());
        var saved = products.save(current.addMedia(media));
        var storedMedia = saved.media().stream()
                .filter(candidate -> candidate.id().equals(media.id()))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        audit.record(
                "PRODUCT_MEDIA",
                storedMedia.id().value(),
                "CREATED",
                null,
                storedMedia.version(),
                Map.of(
                        "productId", saved.id().value().toString(),
                        "productVersion", Long.toString(saved.version()),
                        "mediaType", storedMedia.type().name()));
        idempotency.complete(
                command.actorScope(), command.idempotencyKey(), media.id().value());
        return mediaView(saved, media.id());
    }

    /** Updates media presentation metadata while retaining storage identity. */
    @Transactional
    public AdminMediaView updateMedia(UpdateMediaCommand command) {
        var current = loadExpected(command.productId(), command.expectedVersion());
        var media = current.media().stream()
                .filter(candidate -> candidate.id().equals(command.mediaId()))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        var variantId = command.variantSpecified()
                ? command.variantId()
                : media.variantId().orElse(null);
        var replacement = media.revise(
                variantId,
                command.alt() == null ? media.alt() : command.alt(),
                command.displayOrder() == null ? media.displayOrder() : command.displayOrder(),
                command.primary() == null ? media.primary() : command.primary());
        var saved = products.save(current.updateMedia(replacement));
        var storedMedia = saved.media().stream()
                .filter(candidate -> candidate.id().equals(replacement.id()))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        audit.record(
                "PRODUCT_MEDIA",
                storedMedia.id().value(),
                "UPDATED",
                media.version(),
                storedMedia.version(),
                Map.of(
                        "productId", saved.id().value().toString(),
                        "productVersion", Long.toString(saved.version()),
                        "mediaType", storedMedia.type().name()));
        return mediaView(saved, replacement.id());
    }

    private Product load(ProductId id) {
        return products.findById(id).orElseThrow(AdminCatalogProductNotFoundException::new);
    }

    private Product loadExpected(ProductId id, long expectedVersion) {
        var product = load(id);
        if (product.version() != expectedVersion) {
            throw new StaleCatalogVersionException();
        }
        return product;
    }

    private AdminProductView view(Product product) {
        var metadata = adminReader.findMetadata(product.id()).orElseThrow(AdminCatalogProductNotFoundException::new);
        return new AdminProductView(
                product, metadata.createdAt(), metadata.updatedAt(), CatalogVersionEtag.format(product.version()));
    }

    private static AdminVariantView variantView(Product product, VariantId variantId) {
        var variant = product.variants().stream()
                .filter(candidate -> candidate.id().equals(variantId))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        return new AdminVariantView(variant, CatalogVersionEtag.format(product.version()));
    }

    private static AdminMediaView mediaView(Product product, MediaId mediaId) {
        var media = product.media().stream()
                .filter(candidate -> candidate.id().equals(mediaId))
                .findFirst()
                .orElseThrow(CatalogProductChildNotFoundException::new);
        return new AdminMediaView(media, CatalogVersionEtag.format(product.version()));
    }

    private void ensureActiveProductRemainsPublishable(Product product) {
        if (product.status() != ProductStatus.ACTIVE) {
            return;
        }
        var violations = product.publicationViolations(activeCategories.findActive(product.categoryIds()));
        if (!violations.isEmpty()) {
            throw new ProductInvariantViolation(ProductInvariant.PRODUCT_NOT_PUBLISHABLE, violations);
        }
    }

    private static String fingerprint(CreateCommand command) {
        return productHash(
                        new RepresentationHasher(),
                        command.slug(),
                        command.content(),
                        command.price(),
                        command.primaryCategoryId(),
                        command.categoryIds(),
                        command.collectionIds(),
                        command.characteristics())
                .hexDigest();
    }

    private static String fingerprint(TransitionCommand command) {
        return new RepresentationHasher()
                .add(command.productId().value())
                .add(command.expectedVersion())
                .add(command.transition().name())
                .hexDigest();
    }

    private static String fingerprint(CreateVariantCommand command) {
        var hash = new RepresentationHasher()
                .add(command.productId().value())
                .add(command.expectedVersion())
                .add(command.sku().value())
                .add(command.label())
                .add(command.displayOrder());
        addAttributes(hash, command.attributes());
        return hash.hexDigest();
    }

    private static String fingerprint(CreateMediaCommand command) {
        var hash = new RepresentationHasher()
                .add(command.productId().value())
                .add(command.expectedVersion())
                .add(command.variantId() == null ? 0 : 1);
        if (command.variantId() != null) {
            hash.add(command.variantId().value());
        }
        return hash.add(command.objectKey())
                .add(command.contentType())
                .add(command.width())
                .add(command.height())
                .add(command.alt())
                .add(command.displayOrder())
                .add(command.primary() ? 1 : 0)
                .hexDigest();
    }

    private static RepresentationHasher productHash(
            RepresentationHasher hash,
            ProductSlug slug,
            ProductContent content,
            ProductPrice price,
            CategoryId primaryCategoryId,
            Set<CategoryId> categoryIds,
            Set<CollectionId> collectionIds,
            List<AttributeValue> characteristics) {
        hash.add(slug.value())
                .add(content.name())
                .add(content.shortDescription())
                .add(content.description())
                .add(price.minorUnits())
                .add(primaryCategoryId.value());
        categoryIds.stream().map(CategoryId::value).sorted().forEach(hash::add);
        collectionIds.stream().map(CollectionId::value).sorted().forEach(hash::add);
        addAttributes(hash, characteristics);
        return hash;
    }

    private static void addAttributes(RepresentationHasher hash, List<AttributeValue> attributes) {
        hash.add(attributes.size());
        attributes.stream()
                .sorted(Comparator.comparingInt(AttributeValue::displayOrder).thenComparing(AttributeValue::code))
                .forEach(attribute -> {
                    hash.add(attribute.code())
                            .add(attribute.displayName())
                            .add(attribute.type().name())
                            .add(attribute.value())
                            .add(attribute.label())
                            .add(attribute.colorHex() == null ? 0 : 1)
                            .add(attribute.variantDefining() ? 1 : 0)
                            .add(attribute.displayOrder());
                    if (attribute.colorHex() != null) {
                        hash.add(attribute.colorHex());
                    }
                });
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Catalog " + field + " must not be blank");
        }
        return value;
    }

    public enum Transition {
        PUBLISH,
        ARCHIVE,
        RESTORE
    }

    public record CreateCommand(
            ProductSlug slug,
            ProductContent content,
            ProductPrice price,
            CategoryId primaryCategoryId,
            Set<CategoryId> categoryIds,
            Set<CollectionId> collectionIds,
            List<AttributeValue> characteristics,
            String actorScope,
            String idempotencyKey) {}

    public record UpdateCommand(
            ProductId productId,
            long expectedVersion,
            @Nullable ProductSlug slug,
            @Nullable String name,
            @Nullable String shortDescription,
            @Nullable String description,
            @Nullable ProductPrice price,
            @Nullable CategoryId primaryCategoryId,
            @Nullable Set<CategoryId> categoryIds,
            @Nullable Set<CollectionId> collectionIds,
            @Nullable List<AttributeValue> characteristics,
            @Nullable ProductMerchandising merchandising) {}

    public record TransitionCommand(
            ProductId productId,
            long expectedVersion,
            Transition transition,
            String actorScope,
            String idempotencyKey) {}

    public record CreateVariantCommand(
            ProductId productId,
            long expectedVersion,
            Sku sku,
            String label,
            int displayOrder,
            List<AttributeValue> attributes,
            String actorScope,
            String idempotencyKey) {}

    public record UpdateVariantCommand(
            ProductId productId,
            VariantId variantId,
            long expectedVersion,
            @Nullable String label,
            @Nullable VariantStatus status,
            @Nullable Integer displayOrder,
            @Nullable List<AttributeValue> attributes) {}

    public record CreateMediaCommand(
            ProductId productId,
            long expectedVersion,
            @Nullable VariantId variantId,
            String objectKey,
            String contentType,
            int width,
            int height,
            String alt,
            int displayOrder,
            boolean primary,
            String actorScope,
            String idempotencyKey) {}

    public record UpdateMediaCommand(
            ProductId productId,
            MediaId mediaId,
            long expectedVersion,
            boolean variantSpecified,
            @Nullable VariantId variantId,
            @Nullable String alt,
            @Nullable Integer displayOrder,
            @Nullable Boolean primary) {}
}
