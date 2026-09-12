package ru.amra.market.catalog.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.port.CatalogProductReferenceReader;
import ru.amra.market.catalog.application.port.MediaDeliveryUrlProvider;
import ru.amra.market.catalog.application.port.ProductRepository;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.Product;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.ProductStatus;
import ru.amra.market.catalog.domain.VariantStatus;

/** Resolves a public product detail or its direct canonical redirect. */
@Service
public class GetCatalogProduct {

    private final ProductRepository products;
    private final CatalogProductReferenceReader references;
    private final MediaDeliveryUrlProvider mediaUrls;

    public GetCatalogProduct(
            ProductRepository products, CatalogProductReferenceReader references, MediaDeliveryUrlProvider mediaUrls) {
        this.products = products;
        this.references = references;
        this.mediaUrls = mediaUrls;
    }

    /** Hides every non-active state and resolves historical slugs without redirect chains. */
    @Transactional(readOnly = true)
    public CatalogProductResolution execute(String requestedSlug) {
        var lookup =
                products.findBySlug(new ProductSlug(requestedSlug)).orElseThrow(CatalogProductNotFoundException::new);
        var product = lookup.product();
        if (product.status() != ProductStatus.ACTIVE) {
            throw new CatalogProductNotFoundException();
        }
        if (lookup.alias()) {
            return new CatalogProductResolution.Redirect(product.slug().value());
        }
        return new CatalogProductResolution.Found(detail(product));
    }

    private CatalogProductDetail detail(Product product) {
        var productReferences = references.findFor(product.id());
        var categories = productReferences.categories().stream()
                .map(category ->
                        new CatalogProductDetail.CategorySummary(category.id(), category.slug(), category.name()))
                .toList();
        var collections = productReferences.collections().stream()
                .map(collection -> new CatalogProductDetail.CollectionSummary(
                        collection.id(), collection.slug(), collection.name()))
                .toList();
        var characteristics = product.characteristics().stream()
                .map(GetCatalogProduct::attribute)
                .toList();
        var activeVariants = product.variants().stream()
                .filter(variant -> variant.status() == VariantStatus.ACTIVE)
                .map(variant -> new CatalogProductDetail.Variant(
                        variant.id().value(),
                        variant.sku().value(),
                        variant.label(),
                        variant.displayOrder(),
                        variant.attributes().stream()
                                .map(GetCatalogProduct::attribute)
                                .toList()))
                .toList();
        var activeVariantIds = activeVariants.stream()
                .map(CatalogProductDetail.Variant::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var media = product.media().stream()
                .filter(item -> item.variantId()
                        .map(variantId -> activeVariantIds.contains(variantId.value()))
                        .orElse(true))
                .map(item -> new CatalogProductPage.Media(
                        item.id().value(),
                        mediaUrls.publicUrl(item.id().value(), item.objectKey()),
                        item.alt(),
                        item.width(),
                        item.height(),
                        item.displayOrder()))
                .toList();
        var publishedAt = product.publishedAt().orElseThrow();
        var content = product.content();
        var withoutEtag = new CatalogProductDetail(
                product.id().value(),
                product.slug().value(),
                content.name(),
                content.shortDescription(),
                content.description(),
                categories,
                collections,
                characteristics,
                media,
                activeVariants,
                publishedAt,
                "");
        return new CatalogProductDetail(
                withoutEtag.id(),
                withoutEtag.slug(),
                withoutEtag.name(),
                withoutEtag.shortDescription(),
                withoutEtag.description(),
                withoutEtag.categories(),
                withoutEtag.collections(),
                withoutEtag.characteristics(),
                withoutEtag.media(),
                withoutEtag.variants(),
                withoutEtag.publishedAt(),
                etag(withoutEtag));
    }

    private static CatalogProductPage.AttributeValue attribute(AttributeValue value) {
        return new CatalogProductPage.AttributeValue(
                value.code(), value.displayName(), value.type(), value.value(), value.label(), value.colorHex());
    }

    private static String etag(CatalogProductDetail detail) {
        var hash = new RepresentationHasher()
                .add(detail.id())
                .add(detail.slug())
                .add(detail.name())
                .add(detail.shortDescription())
                .add(detail.description())
                .add(detail.publishedAt())
                .add(detail.categories().size());
        detail.categories()
                .forEach(
                        category -> hash.add(category.id()).add(category.slug()).add(category.name()));
        hash.add(detail.collections().size());
        detail.collections()
                .forEach(collection ->
                        hash.add(collection.id()).add(collection.slug()).add(collection.name()));
        addAttributes(hash, detail.characteristics());
        hash.add(detail.media().size());
        detail.media()
                .forEach(media -> hash.add(media.id())
                        .add(media.url().toString())
                        .add(media.alt())
                        .add(media.width())
                        .add(media.height())
                        .add(media.displayOrder()));
        hash.add(detail.variants().size());
        detail.variants().forEach(variant -> {
            hash.add(variant.id()).add(variant.sku()).add(variant.label()).add(variant.displayOrder());
            addAttributes(hash, variant.attributes());
        });
        return hash.etag();
    }

    private static void addAttributes(RepresentationHasher hash, List<CatalogProductPage.AttributeValue> attributes) {
        hash.add(attributes.size());
        attributes.forEach(attribute -> hash.add(attribute.definitionCode())
                .add(attribute.definitionName())
                .add(attribute.type().name())
                .add(attribute.valueCode())
                .add(attribute.label()));
    }
}
