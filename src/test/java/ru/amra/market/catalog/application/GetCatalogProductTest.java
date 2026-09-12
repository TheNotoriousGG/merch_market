package ru.amra.market.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.amra.market.catalog.application.port.CatalogProductReferenceReader;
import ru.amra.market.catalog.application.port.ProductLookup;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.catalog.domain.AttributeType;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.MediaId;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductContent;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductMedia;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductVariant;
import ru.amra.market.catalog.domain.Sku;
import ru.amra.market.catalog.domain.VariantId;

class GetCatalogProductTest {

    private static final CategoryId CATEGORY_ID = new CategoryId(uuid(1));

    @Test
    void mapsOnlyActivePresentationDataWithoutStorageKeys() {
        var active = activeProduct();
        var products = mock(ProductRepository.class);
        when(products.findBySlug(active.slug())).thenReturn(java.util.Optional.of(new ProductLookup(active, false)));
        CatalogProductReferenceReader references = productId -> new CatalogProductReferenceReader.References(
                List.of(new CatalogProductReferenceReader.CategoryRecord(CATEGORY_ID.value(), "clothes", "Одежда")),
                List.of());
        var useCase = new GetCatalogProduct(
                products, references, (id, objectKey) -> URI.create("https://cdn.example/media/" + id));

        var resolution = useCase.execute("product");

        assertThat(resolution).isInstanceOfSatisfying(CatalogProductResolution.Found.class, found -> {
            assertThat(found.detail().slug()).isEqualTo("product");
            assertThat(found.detail().categories()).hasSize(1);
            assertThat(found.detail().variants()).hasSize(1);
            assertThat(found.detail().media()).hasSize(1);
            assertThat(found.detail().etag()).matches("\"[0-9a-f]{64}\"");
        });
    }

    @Test
    void resolvesAliasDirectlyAndHidesMissingOrDraftProducts() {
        var active = activeProduct();
        var products = mock(ProductRepository.class);
        when(products.findBySlug(new ProductSlug("old")))
                .thenReturn(java.util.Optional.of(new ProductLookup(active, true)));
        when(products.findBySlug(new ProductSlug("draft")))
                .thenReturn(java.util.Optional.of(new ProductLookup(draftProduct(), false)));
        var useCase = new GetCatalogProduct(
                products,
                productId -> new CatalogProductReferenceReader.References(List.of(), List.of()),
                (id, objectKey) -> URI.create("https://cdn.example/media/" + id));

        assertThat(useCase.execute("old")).isEqualTo(new CatalogProductResolution.Redirect("product"));
        assertThatThrownBy(() -> useCase.execute("draft")).isInstanceOf(CatalogProductNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute("missing")).isInstanceOf(CatalogProductNotFoundException.class);
    }

    private static Product activeProduct() {
        var active = draftProduct()
                .addVariant(variant(uuid(3), "AMR-ACTIVE-M", "M", 0))
                .addVariant(variant(uuid(4), "AMR-ARCHIVED-L", "L", 1));
        active = active.addMedia(ProductMedia.image(
                new MediaId(uuid(5)),
                new VariantId(uuid(3)),
                "private/product.webp",
                "image/webp",
                1200,
                1500,
                "Товар",
                0,
                true));
        active = active.publish(Set.of(CATEGORY_ID), Instant.parse("2026-08-19T12:00:00Z"));
        return active.archiveVariant(new VariantId(uuid(4)));
    }

    private static Product draftProduct() {
        return Product.create(
                new ProductId(uuid(2)),
                new ProductSlug("product"),
                new ProductContent("Товар", "Кратко", "Подробно"),
                new ru.amra.market.catalog.domain.ProductPrice(549_000),
                CATEGORY_ID,
                Set.of(CATEGORY_ID),
                Set.of(),
                List.of(new AttributeValue("material", "Материал", AttributeType.TEXT, "Хлопок", false, 0)));
    }

    private static ProductVariant variant(UUID id, String sku, String size, int order) {
        return ProductVariant.create(
                new VariantId(id),
                new Sku(sku),
                size,
                order,
                List.of(new AttributeValue("size", "Размер", AttributeType.SIZE, size, true, 0)));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("01991a80-0000-7000-8000-%012d".formatted(suffix));
    }
}
