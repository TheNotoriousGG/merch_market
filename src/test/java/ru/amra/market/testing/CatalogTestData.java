package ru.amra.market.testing;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.catalog.domain.AttributeType;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryName;
import ru.amra.market.catalog.domain.CategorySlug;
import ru.amra.market.catalog.domain.CategoryStatus;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.MediaId;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductContent;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductMedia;
import ru.amra.market.catalog.domain.ProductMerchandising;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductVariant;
import ru.amra.market.catalog.domain.Sku;
import ru.amra.market.catalog.domain.VariantId;

/** Reusable PostgreSQL-backed catalog fixture builder for slice integration tests. */
public final class CatalogTestData {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final JdbcTemplate jdbc;

    public CatalogTestData(CategoryRepository categories, ProductRepository products, JdbcTemplate jdbc) {
        this.categories = categories;
        this.products = products;
        this.jdbc = jdbc;
    }

    public Category activeCategory(@Nullable CategoryId parentId, String slug, String name, int displayOrder) {
        var hidden = categories.save(Category.create(
                new CategoryId(uuidV7()), parentId, new CategorySlug(slug), new CategoryName(name), displayOrder));
        return categories.save(
                hidden.change(parentId, hidden.slug(), hidden.name(), hidden.displayOrder(), CategoryStatus.ACTIVE));
    }

    public ProductVariant variant(String sku, String color, String size, int displayOrder) {
        return ProductVariant.create(
                new VariantId(uuidV7()),
                new Sku(sku),
                color + " / " + size,
                displayOrder,
                List.of(
                        new AttributeValue("color", "Цвет", AttributeType.COLOR, color, true, 0),
                        new AttributeValue("size", "Размер", AttributeType.SIZE, size, true, 1)));
    }

    public Product activeProduct(
            Category category,
            String slug,
            String name,
            Instant publishedAt,
            List<ProductVariant> variants,
            Set<CollectionId> collections) {
        var draft = products.save(Product.create(
                new ProductId(uuidV7()),
                new ProductSlug(slug),
                new ProductContent(name, "Кратко: " + name, "Подробное описание: " + name),
                new ru.amra.market.catalog.domain.ProductPrice(549_000),
                category.id(),
                Set.of(category.id()),
                collections,
                List.of(new AttributeValue("material", "Материал", AttributeType.TEXT, "Хлопок", false, 0))));
        draft = products.save(draft.revise(
                draft.slug(),
                draft.content(),
                draft.primaryCategoryId(),
                draft.categoryIds(),
                draft.collectionIds(),
                draft.characteristics(),
                new ProductMerchandising(true, null, false, null, false),
                draft.price().orElseThrow()));
        var current = draft;
        for (var variant : variants) {
            current = products.save(current.addVariant(variant));
        }
        var primaryVariant = current.variants().getFirst();
        current = products.save(current.addMedia(ProductMedia.image(
                new MediaId(uuidV7()),
                primaryVariant.id(),
                "catalog/" + current.id().value() + "/primary.webp",
                "image/webp",
                1200,
                1500,
                name,
                0,
                true)));
        return products.save(current.publish(Set.of(category.id()), publishedAt));
    }

    public Product draftProduct(Category category, String slug, String name) {
        return products.save(Product.create(
                new ProductId(uuidV7()),
                new ProductSlug(slug),
                new ProductContent(name, "Кратко: " + name, "Подробное описание: " + name),
                category.id(),
                Set.of(category.id()),
                Set.of(),
                List.of()));
    }

    public CollectionId activeCollection(String slug, String name) {
        var id = new CollectionId(uuidV7());
        jdbc.update("""
                insert into catalog_collections (
                    id, slug, name, description, status, display_order, version
                ) values (?, ?, ?, ?, 'ACTIVE', 0, 0)
                """, id.value(), slug, name, "Подборка " + name);
        return id;
    }

    public UUID uuidV7() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
