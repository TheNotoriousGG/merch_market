package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.port.CategoryRepository;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.catalog.domain.AttributeType;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.Category;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryName;
import ru.amra.market.catalog.domain.CategorySlug;
import ru.amra.market.catalog.domain.MediaId;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductContent;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductMedia;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductStatus;
import ru.amra.market.catalog.domain.ProductVariant;
import ru.amra.market.catalog.domain.Sku;
import ru.amra.market.catalog.domain.VariantId;
import ru.amra.market.catalog.domain.VariantStatus;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@Transactional
class ProductRepositoryIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant PUBLICATION_TIME = Instant.parse("2026-08-19T11:00:00Z");

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void roundTripsCompleteAggregateAndAdvancesVersionsForChildOnlyChanges() {
        var category = savedCategory();
        var draft = products.save(draft(category.id(), "futbolka-seriya-01"));
        var withVariant = products.save(draft.addVariant(variant("AMR-TS01-BLK-M")));
        var withMedia = products.save(withVariant.addMedia(
                primaryMedia(withVariant.variants().getFirst().id())));
        var published = products.save(withMedia.publish(Set.of(category.id()), PUBLICATION_TIME));

        var reloaded = products.findById(published.id()).orElseThrow();

        assertThat(ProductState.from(reloaded)).isEqualTo(ProductState.from(published));
        assertThat(reloaded.version()).isEqualTo(3);
        assertThat(reloaded.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(reloaded.variants()).hasSize(1);
        assertThat(reloaded.media()).hasSize(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from catalog_variant_attribute_values where variant_id = ?",
                        Long.class,
                        reloaded.variants().getFirst().id().value()))
                .isEqualTo(2);
    }

    @Test
    void resolvesEveryHistoricalAliasDirectlyAndPreservesCanonicalLookup() {
        var category = savedCategory();
        var original = products.save(draft(category.id(), "old-slug"));
        var renamed = products.save(original.changeSlug(new ProductSlug("canonical-slug")));

        var aliasLookup = products.findBySlug(new ProductSlug("old-slug")).orElseThrow();
        var canonicalLookup =
                products.findBySlug(new ProductSlug("canonical-slug")).orElseThrow();

        assertThat(aliasLookup.alias()).isTrue();
        assertThat(aliasLookup.product().slug()).isEqualTo(new ProductSlug("canonical-slug"));
        assertThat(canonicalLookup.alias()).isFalse();
        assertThat(canonicalLookup.product().id()).isEqualTo(renamed.id());
    }

    @Test
    void rejectsStaleAndForgedVersionsButTreatsIdenticalSaveAsIdempotent() {
        var category = savedCategory();
        var created = products.save(draft(category.id(), "product"));
        var firstReader = products.findById(created.id()).orElseThrow();
        var secondReader = products.findById(created.id()).orElseThrow();
        var winner = products.save(firstReader.changeSlug(new ProductSlug("winner")));

        assertThat(products.save(winner).version()).isEqualTo(winner.version());
        assertThatThrownBy(() -> products.save(secondReader.changeSlug(new ProductSlug("loser"))))
                .isInstanceOf(ConcurrentCatalogModificationException.class);

        var unknown = draft(category.id(), "unknown");
        var forged = Product.restore(
                unknown.id(),
                unknown.slug(),
                unknown.aliases(),
                unknown.content(),
                unknown.status(),
                unknown.primaryCategoryId(),
                unknown.categoryIds(),
                unknown.collectionIds(),
                unknown.characteristics(),
                unknown.variants(),
                unknown.media(),
                null,
                2);
        assertThatThrownBy(() -> products.save(forged)).isInstanceOf(ConcurrentCatalogModificationException.class);
    }

    @Test
    void persistsVariantArchiveWithoutReleasingSkuIdentity() {
        var category = savedCategory();
        var withVariant =
                products.save(products.save(draft(category.id(), "product")).addVariant(variant("AMR-ARCHIVE-1")));
        var variantId = withVariant.variants().getFirst().id();

        var saved = products.save(withVariant.archiveVariant(variantId));

        assertThat(saved.variants().getFirst().status()).isEqualTo(VariantStatus.ARCHIVED);
        assertThat(saved.variants().getFirst().sku()).isEqualTo(new Sku("AMR-ARCHIVE-1"));
        assertThat(saved.variants().getFirst().version()).isEqualTo(1);
    }

    private Category savedCategory() {
        return categories.save(Category.create(
                new CategoryId(uuidV7()),
                null,
                new CategorySlug("clothes-" + UUID.randomUUID().toString().substring(0, 8)),
                new CategoryName("Одежда"),
                0));
    }

    private Product draft(CategoryId categoryId, String slug) {
        return Product.create(
                new ProductId(uuidV7()),
                new ProductSlug(slug),
                new ProductContent("Футболка", "Короткое описание", "Полное описание"),
                new ru.amra.market.catalog.domain.ProductPrice(549_000),
                categoryId,
                Set.of(categoryId),
                Set.of(),
                List.of(new AttributeValue("material", "Материал", AttributeType.TEXT, "Хлопок", false, 0)));
    }

    private ProductVariant variant(String sku) {
        return ProductVariant.create(
                new VariantId(uuidV7()),
                new Sku(sku),
                "Графит / M",
                0,
                List.of(
                        new AttributeValue("color", "Цвет", AttributeType.COLOR, "GRAPHITE", true, 0),
                        new AttributeValue("size", "Размер", AttributeType.SIZE, "M", true, 1)));
    }

    private ProductMedia primaryMedia(VariantId variantId) {
        return ProductMedia.image(
                new MediaId(uuidV7()),
                variantId,
                "catalog/" + UUID.randomUUID() + "/primary.webp",
                "image/webp",
                1200,
                1500,
                "Футболка Амра",
                0,
                true);
    }

    private UUID uuidV7() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
