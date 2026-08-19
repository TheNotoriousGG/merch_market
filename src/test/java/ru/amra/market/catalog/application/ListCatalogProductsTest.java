package ru.amra.market.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ru.amra.market.catalog.application.port.CatalogProductListReader;
import ru.amra.market.catalog.domain.AttributeType;

class ListCatalogProductsTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final UUID PRODUCT_ID = UUID.fromString("01991a80-0000-7000-8000-000000000101");
    private static final UUID MEDIA_ID = UUID.fromString("01991a80-0000-7000-8000-000000000102");

    @Test
    void mapsPrivateProjectionToPublicPageAndUsesInjectedClockForNewWindow() {
        var observedCutoff = new AtomicReference<Instant>();
        CatalogProductListReader reader = (criteria, newAfter) -> {
            observedCutoff.set(newAfter);
            return new CatalogProductListReader.Result(List.of(product()), 25);
        };
        var useCase = new ListCatalogProducts(
                reader, id -> URI.create("https://cdn.example/media/" + id), Clock.fixed(NOW, ZoneOffset.UTC));

        var result = useCase.execute(criteria());

        assertThat(observedCutoff).hasValue(NOW.minus(ListCatalogProducts.NEW_PRODUCT_WINDOW));
        assertThat(result.page()).isEqualTo(new CatalogProductPage.Metadata(0, 24, 25, 2));
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.slug()).isEqualTo("hoodie");
            assertThat(item.primaryMedia().url()).isEqualTo(URI.create("https://cdn.example/media/" + MEDIA_ID));
            assertThat(item.variantOptions()).singleElement().satisfies(option -> {
                assertThat(option.type()).isEqualTo(AttributeType.SIZE);
                assertThat(option.values())
                        .extracting(CatalogProductPage.AttributeValue::valueCode)
                        .containsExactly("M");
            });
        });
        assertThat(result.etag()).matches("\"[0-9a-f]{64}\"");
    }

    @Test
    void representsAnEmptyPageWithZeroTotalPages() {
        var useCase = new ListCatalogProducts(
                (criteria, newAfter) -> new CatalogProductListReader.Result(List.of(), 0),
                id -> URI.create("https://cdn.example/media/" + id),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = useCase.execute(criteria());

        assertThat(result.items()).isEmpty();
        assertThat(result.page().totalPages()).isZero();
    }

    private static CatalogProductListCriteria criteria() {
        return new CatalogProductListCriteria(
                0, 24, null, null, null, false, List.of(), List.of(), CatalogProductSort.MANUAL);
    }

    private static CatalogProductListReader.ProductRecord product() {
        var value = new CatalogProductListReader.AttributeValueRecord("size", "Размер", AttributeType.SIZE, "M", "M");
        var option =
                new CatalogProductListReader.VariantOptionRecord("size", "Размер", AttributeType.SIZE, List.of(value));
        return new CatalogProductListReader.ProductRecord(
                PRODUCT_ID,
                "hoodie",
                "Худи",
                "Краткое описание",
                NOW,
                new CatalogProductListReader.MediaRecord(MEDIA_ID, "Худи", 1200, 1500, 0),
                List.of(option));
    }
}
