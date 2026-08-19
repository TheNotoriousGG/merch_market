package ru.amra.market.catalog.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.CatalogProductListReader;
import ru.amra.market.catalog.application.port.MediaDeliveryUrlProvider;

/** Executes the public product-list query and maps private persistence projections to public data. */
@Service
public class ListCatalogProducts {

    static final Duration NEW_PRODUCT_WINDOW = Duration.ofDays(30);

    private final CatalogProductListReader reader;
    private final MediaDeliveryUrlProvider mediaUrls;
    private final Clock clock;

    public ListCatalogProducts(CatalogProductListReader reader, MediaDeliveryUrlProvider mediaUrls, Clock clock) {
        this.reader = reader;
        this.mediaUrls = mediaUrls;
        this.clock = clock;
    }

    /** Returns a stable exact page and a strong ETag of the public representation. */
    @Transactional(readOnly = true)
    public CatalogProductPage execute(CatalogProductListCriteria criteria) {
        var raw = reader.find(criteria, clock.instant().minus(NEW_PRODUCT_WINDOW));
        var items = raw.products().stream().map(this::toItem).toList();
        var totalPages = raw.totalElements() == 0
                ? 0
                : Math.toIntExact(Math.floorDiv(raw.totalElements() - 1, criteria.size()) + 1);
        var metadata =
                new CatalogProductPage.Metadata(criteria.page(), criteria.size(), raw.totalElements(), totalPages);
        return new CatalogProductPage(items, metadata, etag(items, metadata));
    }

    private CatalogProductPage.Item toItem(CatalogProductListReader.ProductRecord product) {
        var media = product.primaryMedia();
        var publicMedia = new CatalogProductPage.Media(
                media.id(),
                mediaUrls.publicUrl(media.id()),
                media.alt(),
                media.width(),
                media.height(),
                media.displayOrder());
        var options = product.variantOptions().stream()
                .map(option -> new CatalogProductPage.VariantOption(
                        option.definitionCode(),
                        option.definitionName(),
                        option.type(),
                        option.values().stream()
                                .map(value -> new CatalogProductPage.AttributeValue(
                                        value.definitionCode(),
                                        value.definitionName(),
                                        value.type(),
                                        value.valueCode(),
                                        value.label(),
                                        null))
                                .toList()))
                .toList();
        return new CatalogProductPage.Item(
                product.id(),
                product.slug(),
                product.name(),
                product.shortDescription(),
                publicMedia,
                product.publishedAt(),
                options);
    }

    private static String etag(List<CatalogProductPage.Item> items, CatalogProductPage.Metadata metadata) {
        var hash = new RepresentationHasher()
                .add(metadata.page())
                .add(metadata.size())
                .add(metadata.totalElements())
                .add(metadata.totalPages())
                .add(items.size());
        for (var item : items) {
            hash.add(item.id())
                    .add(item.slug())
                    .add(item.name())
                    .add(item.shortDescription())
                    .add(item.publishedAt());
            var media = item.primaryMedia();
            hash.add(media.id())
                    .add(media.url().toString())
                    .add(media.alt())
                    .add(media.width())
                    .add(media.height())
                    .add(media.displayOrder())
                    .add(item.variantOptions().size());
            for (var option : item.variantOptions()) {
                hash.add(option.definitionCode())
                        .add(option.definitionName())
                        .add(option.type().name())
                        .add(option.values().size());
                for (var value : option.values()) {
                    hash.add(value.valueCode()).add(value.label());
                }
            }
        }
        return hash.etag();
    }
}
