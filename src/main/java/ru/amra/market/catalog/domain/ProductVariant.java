package ru.amra.market.catalog.domain;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable-SKU product variant. */
public final class ProductVariant {

    private final VariantId id;
    private final Sku sku;
    private final String label;
    private final VariantStatus status;
    private final int displayOrder;
    private final List<AttributeValue> attributes;
    private final long version;

    private ProductVariant(
            VariantId id,
            Sku sku,
            String label,
            VariantStatus status,
            int displayOrder,
            List<AttributeValue> attributes,
            long version) {
        this.id = id;
        this.sku = sku;
        this.label = label(label);
        if (status == null) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_LIFECYCLE_TRANSITION, "Variant status must not be null");
        }
        this.status = status;
        if (displayOrder < 0) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_ATTRIBUTE, "Variant display order must not be negative");
        }
        this.displayOrder = displayOrder;
        this.attributes = validateAttributes(attributes);
        if (version < 0) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_VERSION, "Variant version must not be negative");
        }
        this.version = version;
    }

    /** Creates an active variant with an immutable business SKU. */
    public static ProductVariant create(
            VariantId id, Sku sku, String label, int displayOrder, List<AttributeValue> attributes) {
        return new ProductVariant(id, sku, label, VariantStatus.ACTIVE, displayOrder, attributes, 0);
    }

    /** Restores a persisted variant without applying a business mutation. */
    public static ProductVariant restore(
            VariantId id,
            Sku sku,
            String label,
            VariantStatus status,
            int displayOrder,
            List<AttributeValue> attributes,
            long version) {
        return new ProductVariant(id, sku, label, status, displayOrder, attributes, version);
    }

    /** Archives the variant while retaining its SKU identity. */
    public ProductVariant archive() {
        if (status == VariantStatus.ARCHIVED) {
            return this;
        }
        return new ProductVariant(id, sku, label, VariantStatus.ARCHIVED, displayOrder, attributes, version + 1);
    }

    /** Changes presentation and defining attributes without allowing the SKU identity to change. */
    public ProductVariant revise(
            String newLabel, VariantStatus newStatus, int newDisplayOrder, List<AttributeValue> newAttributes) {
        if (label.equals(newLabel)
                && status == newStatus
                && displayOrder == newDisplayOrder
                && attributes.equals(newAttributes)) {
            return this;
        }
        if (status == VariantStatus.ARCHIVED && newStatus != VariantStatus.ARCHIVED) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_LIFECYCLE_TRANSITION, "Archived variant cannot be reactivated");
        }
        return new ProductVariant(
                id,
                sku,
                Objects.requireNonNull(newLabel),
                Objects.requireNonNull(newStatus),
                newDisplayOrder,
                newAttributes,
                version + 1);
    }

    /** Returns the normalized defining combination independent of input ordering. */
    public String definingCombination() {
        return attributes.stream()
                .filter(AttributeValue::variantDefining)
                .map(AttributeValue::combinationPart)
                .sorted()
                .reduce((left, right) -> left + '|' + right)
                .orElseThrow(() -> new ProductInvariantViolation(
                        ProductInvariant.INVALID_ATTRIBUTE,
                        "Variant must contain at least one variant-defining attribute"));
    }

    public VariantId id() {
        return id;
    }

    public Sku sku() {
        return sku;
    }

    public String label() {
        return label;
    }

    public VariantStatus status() {
        return status;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public List<AttributeValue> attributes() {
        return attributes;
    }

    public long version() {
        return version;
    }

    private static List<AttributeValue> validateAttributes(List<AttributeValue> source) {
        if (source == null || source.isEmpty()) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_ATTRIBUTE, "Variant attributes must not be empty");
        }
        var codes = new HashSet<String>();
        for (var attribute : source) {
            if (!codes.add(attribute.code())) {
                throw new ProductInvariantViolation(
                        ProductInvariant.INVALID_ATTRIBUTE, "Variant attribute codes must be unique");
            }
        }
        var result = source.stream()
                .sorted(Comparator.comparingInt(AttributeValue::displayOrder).thenComparing(AttributeValue::code))
                .toList();
        if (result.stream().noneMatch(AttributeValue::variantDefining)) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_ATTRIBUTE, "Variant must contain at least one variant-defining attribute");
        }
        return result;
    }

    private static String label(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.strip().length() > 160) {
            throw new ProductInvariantViolation(
                    ProductInvariant.INVALID_ATTRIBUTE, "Variant label must contain 1-160 characters");
        }
        return candidate.strip();
    }
}
