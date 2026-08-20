package ru.amra.market.catalog.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductStatus;
import ru.amra.market.catalog.domain.ProductPrice;

@Entity
@Table(name = "catalog_products")
class ProductJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "canonical_slug", nullable = false, length = 120)
    private String canonicalSlug = "";

    @Column(name = "name", nullable = false, length = 200)
    private String name = "";

    @Column(name = "short_description", nullable = false, length = 500)
    private String shortDescription = "";

    @Column(name = "description", nullable = false, length = 10_000)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "primary_category_id", nullable = false)
    private UUID primaryCategoryId = new UUID(0, 0);

    @Column(name = "published_at")
    private @Nullable Instant publishedAt;

    @Column(name = "price_minor")
    private @Nullable Long priceMinor;

    @Column(name = "new_arrival", nullable = false)
    private boolean newArrival;

    @Column(name = "new_until")
    private @Nullable Instant newUntil;

    @Column(name = "on_sale", nullable = false)
    private boolean onSale;

    @Column(name = "sale_percent")
    private @Nullable Integer salePercent;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.EPOCH;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProductJpaEntity() {}

    ProductJpaEntity(Product product) {
        id = product.id().value();
        apply(product);
    }

    void apply(Product product) {
        canonicalSlug = product.slug().value();
        name = product.content().name();
        shortDescription = product.content().shortDescription();
        description = product.content().description();
        status = product.status();
        primaryCategoryId = product.primaryCategoryId().value();
        publishedAt = product.publishedAt().orElse(null);
        priceMinor = product.price().map(ProductPrice::minorUnits).orElse(null);
        newArrival = product.merchandising().newArrival();
        newUntil = product.merchandising().newUntil();
        onSale = product.merchandising().onSale();
        salePercent = product.merchandising().salePercent();
        featured = product.merchandising().featured();
        updatedAt = Instant.now();
    }

    UUID id() {
        return id;
    }

    String canonicalSlug() {
        return canonicalSlug;
    }

    String name() {
        return name;
    }

    String shortDescription() {
        return shortDescription;
    }

    String description() {
        return description;
    }

    ProductStatus status() {
        return status;
    }

    UUID primaryCategoryId() {
        return primaryCategoryId;
    }

    @Nullable Instant publishedAt() {
        return publishedAt;
    }

    @Nullable Long priceMinor() { return priceMinor; }

    boolean newArrival() { return newArrival; }
    @Nullable Instant newUntil() { return newUntil; }
    boolean onSale() { return onSale; }
    @Nullable Integer salePercent() { return salePercent; }
    boolean featured() { return featured; }

    long version() {
        return version;
    }
}
