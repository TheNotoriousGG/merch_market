package ru.amra.market.catalog.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Product aggregate controlling lifecycle, variants, media, assignments and slug history. */
public final class Product {

    private static final Comparator<ProductVariant> VARIANT_ORDER =
            Comparator.comparingInt(ProductVariant::displayOrder).thenComparing(ProductVariant::sku);
    private static final Comparator<ProductMedia> MEDIA_ORDER = Comparator.comparingInt(ProductMedia::displayOrder)
            .thenComparing(media -> media.id().value());

    private final ProductId id;
    private final ProductSlug slug;
    private final Set<ProductSlug> aliases;
    private final ProductContent content;
    private final ProductStatus status;
    private final CategoryId primaryCategoryId;
    private final Set<CategoryId> categoryIds;
    private final Set<CollectionId> collectionIds;
    private final List<AttributeValue> characteristics;
    private final List<ProductVariant> variants;
    private final List<ProductMedia> media;
    private final @Nullable Instant publishedAt;
    private final long version;

    private Product(
            ProductId id,
            ProductSlug slug,
            Set<ProductSlug> aliases,
            ProductContent content,
            ProductStatus status,
            CategoryId primaryCategoryId,
            Set<CategoryId> categoryIds,
            Set<CollectionId> collectionIds,
            List<AttributeValue> characteristics,
            List<ProductVariant> variants,
            List<ProductMedia> media,
            @Nullable Instant publishedAt,
            long version) {
        this.id = id;
        this.slug = slug;
        this.aliases = immutableSet(aliases, ProductInvariant.INVALID_SLUG, "Product aliases must not contain null");
        if (this.aliases.contains(slug)) {
            throw new ProductInvariantViolation(ProductInvariant.SLUG_REUSE, "Canonical slug cannot also be an alias");
        }
        this.content = content;
        if (status == null) {
            throw invalidTransition("Product status must not be null");
        }
        this.status = status;
        this.primaryCategoryId = primaryCategoryId;
        this.categoryIds = immutableSet(
                categoryIds, ProductInvariant.INVALID_ASSIGNMENT, "Product category assignments must not contain null");
        if (this.categoryIds.isEmpty() || !this.categoryIds.contains(primaryCategoryId)) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_ASSIGNMENT,
                    "Primary category must be present in product category assignments");
        }
        this.collectionIds = immutableSet(
                collectionIds,
                ProductInvariant.INVALID_ASSIGNMENT,
                "Product collection assignments must not contain null");
        this.characteristics = validateCharacteristics(characteristics);
        this.variants = validateVariants(variants);
        this.media = validateMedia(media, this.variants);
        if ((status == ProductStatus.DRAFT && publishedAt != null)
                || (status != ProductStatus.DRAFT && publishedAt == null)) {
            throw invalidTransition("Publication time must exist exactly for active or archived products");
        }
        this.publishedAt = publishedAt;
        if (version < 0) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_VERSION, "Product version must not be negative");
        }
        this.version = version;
    }

    /** Creates an unpublished product draft. */
    public static Product create(
            ProductId id,
            ProductSlug slug,
            ProductContent content,
            CategoryId primaryCategoryId,
            Set<CategoryId> categoryIds,
            Set<CollectionId> collectionIds,
            List<AttributeValue> characteristics) {
        return new Product(
                id,
                slug,
                Set.of(),
                content,
                ProductStatus.DRAFT,
                primaryCategoryId,
                categoryIds,
                collectionIds,
                characteristics,
                List.of(),
                List.of(),
                null,
                0);
    }

    /** Restores a persisted aggregate without applying a business mutation. */
    public static Product restore(
            ProductId id,
            ProductSlug slug,
            Set<ProductSlug> aliases,
            ProductContent content,
            ProductStatus status,
            CategoryId primaryCategoryId,
            Set<CategoryId> categoryIds,
            Set<CollectionId> collectionIds,
            List<AttributeValue> characteristics,
            List<ProductVariant> variants,
            List<ProductMedia> media,
            @Nullable Instant publishedAt,
            long version) {
        return new Product(
                id,
                slug,
                aliases,
                content,
                status,
                primaryCategoryId,
                categoryIds,
                collectionIds,
                characteristics,
                variants,
                media,
                publishedAt,
                version);
    }

    /** Changes the canonical slug and records the previous slug as a direct historical alias. */
    public Product changeSlug(ProductSlug newSlug) {
        requireMutable();
        if (slug.equals(newSlug)) {
            return this;
        }
        if (aliases.contains(newSlug)) {
            throw new ProductInvariantViolation(
                    ProductInvariant.SLUG_REUSE, "Historical product slug cannot be reused");
        }
        var newAliases = new HashSet<>(aliases);
        newAliases.add(slug);
        return copy(newSlug, newAliases, content, status, variants, media, publishedAt);
    }

    /** Adds a variant after checking immutable SKU and defining-combination uniqueness. */
    public Product addVariant(ProductVariant variant) {
        requireMutable();
        var updated = new ArrayList<>(variants);
        updated.add(variant);
        return copy(slug, aliases, content, status, updated, media, publishedAt);
    }

    /** Adds ordered media metadata and protects the single-primary-image invariant. */
    public Product addMedia(ProductMedia item) {
        requireMutable();
        var updated = new ArrayList<>(media);
        updated.add(item);
        return copy(slug, aliases, content, status, variants, updated, publishedAt);
    }

    /** Archives a variant without releasing its immutable SKU or defining combination. */
    public Product archiveVariant(VariantId variantId) {
        requireMutable();
        var found = false;
        var updated = new ArrayList<ProductVariant>();
        for (var variant : variants) {
            if (variant.id().equals(variantId)) {
                updated.add(variant.archive());
                found = true;
            } else {
                updated.add(variant);
            }
        }
        if (!found) {
            throw new ProductInvariantViolation(ProductInvariant.INVALID_ID, "Variant does not belong to product");
        }
        if (status == ProductStatus.ACTIVE
                && updated.stream().noneMatch(variant -> variant.status() == VariantStatus.ACTIVE)) {
            throw new ProductInvariantViolation(
                    ProductInvariant.PRODUCT_NOT_PUBLISHABLE, "Active product must retain at least one active variant");
        }
        return copy(slug, aliases, content, status, updated, media, publishedAt);
    }

    /** Publishes a complete draft at the supplied application clock instant. */
    public Product publish(Set<CategoryId> activeCategoryIds, Instant publicationTime) {
        if (status != ProductStatus.DRAFT) {
            throw invalidTransition("Only a draft product can be published");
        }
        var violations = publicationViolations(activeCategoryIds);
        if (!violations.isEmpty()) {
            throw new ProductInvariantViolation(ProductInvariant.PRODUCT_NOT_PUBLISHABLE, violations);
        }
        Objects.requireNonNull(publicationTime, "publicationTime");
        return copy(slug, aliases, content, ProductStatus.ACTIVE, variants, media, publicationTime);
    }

    /** Archives an active product. Archived state is terminal. */
    public Product archive() {
        if (status != ProductStatus.ACTIVE) {
            throw invalidTransition("Only an active product can be archived");
        }
        return copy(slug, aliases, content, ProductStatus.ARCHIVED, variants, media, publishedAt);
    }

    /** Returns all failures that currently prevent publication. */
    public List<String> publicationViolations(Set<CategoryId> activeCategoryIds) {
        var violations = new ArrayList<String>();
        if (!activeCategoryIds.contains(primaryCategoryId)) {
            violations.add("Primary category must be active");
        }
        if (variants.stream().noneMatch(variant -> variant.status() == VariantStatus.ACTIVE)) {
            violations.add("At least one active variant is required");
        }
        if (media.stream().noneMatch(ProductMedia::primary)) {
            violations.add("Exactly one primary image with alt text is required");
        }
        return List.copyOf(violations);
    }

    public ProductId id() {
        return id;
    }

    public ProductSlug slug() {
        return slug;
    }

    public Set<ProductSlug> aliases() {
        return aliases;
    }

    public ProductContent content() {
        return content;
    }

    public ProductStatus status() {
        return status;
    }

    public CategoryId primaryCategoryId() {
        return primaryCategoryId;
    }

    public Set<CategoryId> categoryIds() {
        return categoryIds;
    }

    public Set<CollectionId> collectionIds() {
        return collectionIds;
    }

    public List<AttributeValue> characteristics() {
        return characteristics;
    }

    public List<ProductVariant> variants() {
        return variants;
    }

    public List<ProductMedia> media() {
        return media;
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public long version() {
        return version;
    }

    private Product copy(
            ProductSlug newSlug,
            Set<ProductSlug> newAliases,
            ProductContent newContent,
            ProductStatus newStatus,
            List<ProductVariant> newVariants,
            List<ProductMedia> newMedia,
            @Nullable Instant newPublishedAt) {
        return new Product(
                id,
                newSlug,
                newAliases,
                newContent,
                newStatus,
                primaryCategoryId,
                categoryIds,
                collectionIds,
                characteristics,
                newVariants,
                newMedia,
                newPublishedAt,
                version + 1);
    }

    private void requireMutable() {
        if (status == ProductStatus.ARCHIVED) {
            throw invalidTransition("Archived product is immutable");
        }
    }

    private static List<AttributeValue> validateCharacteristics(List<AttributeValue> source) {
        var codes = new HashSet<String>();
        var result = List.copyOf(source);
        for (var attribute : result) {
            if (attribute.variantDefining() || !codes.add(attribute.code())) {
                throw new ProductInvariantViolation(
                        ProductInvariant.INVALID_ATTRIBUTE,
                        "Product characteristics must have unique non-variant attribute codes");
            }
        }
        return result.stream()
                .sorted(Comparator.comparingInt(AttributeValue::displayOrder).thenComparing(AttributeValue::code))
                .toList();
    }

    private static List<ProductVariant> validateVariants(List<ProductVariant> source) {
        var ids = new HashSet<VariantId>();
        var skus = new HashSet<Sku>();
        var combinations = new HashSet<String>();
        for (var variant : source) {
            if (!ids.add(variant.id())) {
                throw new ProductInvariantViolation(ProductInvariant.INVALID_ID, "Variant identifiers must be unique");
            }
            if (!skus.add(variant.sku())) {
                throw new ProductInvariantViolation(
                        ProductInvariant.DUPLICATE_SKU, "SKU must be unique inside product");
            }
            if (!combinations.add(variant.definingCombination())) {
                throw new ProductInvariantViolation(
                        ProductInvariant.DUPLICATE_VARIANT_COMBINATION,
                        "Variant-defining attribute combination must be unique inside product");
            }
        }
        return source.stream().sorted(VARIANT_ORDER).toList();
    }

    private static List<ProductMedia> validateMedia(List<ProductMedia> source, List<ProductVariant> variants) {
        var ids = new HashSet<MediaId>();
        var variantIds = variants.stream().map(ProductVariant::id).collect(java.util.stream.Collectors.toSet());
        var primaryCount = 0;
        for (var item : source) {
            if (!ids.add(item.id())) {
                throw new ProductInvariantViolation(ProductInvariant.INVALID_ID, "Media identifiers must be unique");
            }
            if (item.variantId().filter(id -> !variantIds.contains(id)).isPresent()) {
                throw new ProductInvariantViolation(
                        ProductInvariant.INVALID_MEDIA, "Media variant must belong to the same product");
            }
            if (item.primary()) {
                primaryCount++;
            }
        }
        if (primaryCount > 1) {
            throw new ProductInvariantViolation(
                    ProductInvariant.MULTIPLE_PRIMARY_MEDIA, "Product can have only one primary image");
        }
        return source.stream().sorted(MEDIA_ORDER).toList();
    }

    private static <T> Set<T> immutableSet(Set<T> source, ProductInvariant invariant, String message) {
        if (source == null || source.stream().anyMatch(Objects::isNull)) {
            throw new ProductInvariantViolation(invariant, message);
        }
        return Set.copyOf(source);
    }

    private static ProductInvariantViolation invalidTransition(String message) {
        return new ProductInvariantViolation(ProductInvariant.INVALID_LIFECYCLE_TRANSITION, message);
    }
}
