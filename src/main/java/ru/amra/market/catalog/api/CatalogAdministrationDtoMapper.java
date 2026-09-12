package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.amra.market.catalog.application.AdminCollectionView;
import ru.amra.market.catalog.application.AdminMediaView;
import ru.amra.market.catalog.application.AdminProductView;
import ru.amra.market.catalog.application.AdminVariantView;
import ru.amra.market.catalog.application.port.MediaDeliveryUrlProvider;
import ru.amra.market.catalog.domain.AttributeType;
import ru.amra.market.catalog.domain.AttributeValue;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.platform.generated.model.AdminCollectionDto;
import ru.amra.market.platform.generated.model.AdminMediaDto;
import ru.amra.market.platform.generated.model.AdminProductDto;
import ru.amra.market.platform.generated.model.AdminVariantDto;
import ru.amra.market.platform.generated.model.CatalogAttributeValueDto;
import ru.amra.market.platform.generated.model.ProductMerchandisingDto;

final class CatalogAdministrationDtoMapper {

    private CatalogAdministrationDtoMapper() {}

    static AdminProductDto product(AdminProductView view, MediaDeliveryUrlProvider mediaUrls) {
        var product = view.product();
        return new AdminProductDto(
                        product.id().value(),
                        product.slug().value(),
                        product.content().name(),
                        product.content().shortDescription(),
                        product.content().description(),
                        AdminProductDto.StatusEnum.valueOf(product.status().name()),
                        product.primaryCategoryId().value(),
                        uuids(product.categoryIds().stream()
                                .map(CategoryId::value)
                                .toList()),
                        uuids(product.collectionIds().stream()
                                .map(CollectionId::value)
                                .toList()),
                        product.characteristics().stream()
                                .map(CatalogAdministrationDtoMapper::attribute)
                                .toList(),
                        product.version(),
                        view.createdAt(),
                        view.updatedAt())
                .publishedAt(product.publishedAt().orElse(null))
                .priceMinor(product.price()
                        .map(ru.amra.market.catalog.domain.ProductPrice::minorUnits)
                        .orElse(null))
                .currency(AdminProductDto.CurrencyEnum.RUB)
                .merchandising(new ProductMerchandisingDto(
                                product.merchandising().newArrival(),
                                product.merchandising().onSale(),
                                product.merchandising().featured())
                        .newUntil(product.merchandising().newUntil())
                        .salePercent(product.merchandising().salePercent()))
                .variants(product.variants().stream()
                        .map(CatalogAdministrationDtoMapper::variant)
                        .toList())
                .media(product.media().stream()
                        .map(item -> media(item, mediaUrls))
                        .toList());
    }

    static AdminVariantDto variant(AdminVariantView view) {
        return variant(view.variant());
    }

    private static AdminVariantDto variant(ru.amra.market.catalog.domain.ProductVariant variant) {
        return new AdminVariantDto(
                variant.id().value(),
                variant.sku().value(),
                variant.label(),
                AdminVariantDto.StatusEnum.valueOf(variant.status().name()),
                variant.displayOrder(),
                variant.attributes().stream()
                        .map(CatalogAdministrationDtoMapper::attribute)
                        .toList(),
                variant.version());
    }

    static AdminMediaDto media(AdminMediaView view, MediaDeliveryUrlProvider mediaUrls) {
        return media(view.media(), mediaUrls);
    }

    private static AdminMediaDto media(
            ru.amra.market.catalog.domain.ProductMedia media, MediaDeliveryUrlProvider mediaUrls) {
        return new AdminMediaDto(
                        media.id().value(),
                        AdminMediaDto.TypeEnum.valueOf(media.type().name()),
                        media.objectKey(),
                        mediaUrls.publicUrl(media.id().value(), media.objectKey()),
                        media.contentType(),
                        media.width(),
                        media.height(),
                        media.alt(),
                        media.displayOrder(),
                        media.primary(),
                        media.version())
                .variantId(media.variantId().map(id -> id.value()).orElse(null));
    }

    static AdminCollectionDto collection(AdminCollectionView view) {
        var collection = view.collection();
        return new AdminCollectionDto(
                collection.id().value(),
                collection.slug().value(),
                collection.name(),
                collection.description(),
                AdminCollectionDto.StatusEnum.valueOf(collection.status().name()),
                collection.displayOrder(),
                collection.productIds().stream().map(ProductId::value).toList(),
                collection.version());
    }

    static List<AttributeValue> attributes(@Nullable List<CatalogAttributeValueDto> source, boolean variantDefining) {
        if (source == null) {
            return List.of();
        }
        var result = new ArrayList<AttributeValue>(source.size());
        for (var index = 0; index < source.size(); index++) {
            var value = source.get(index);
            result.add(new AttributeValue(
                    requireNonNull(value.getDefinitionCode()),
                    requireNonNull(value.getDefinitionName()),
                    AttributeType.valueOf(requireNonNull(value.getType()).name()),
                    requireNonNull(value.getValueCode()),
                    requireNonNull(value.getLabel()),
                    value.getColorHex(),
                    variantDefining,
                    index));
        }
        return List.copyOf(result);
    }

    static Set<CategoryId> categories(List<UUID> source) {
        var result = new LinkedHashSet<CategoryId>();
        source.forEach(id -> result.add(new CategoryId(id)));
        return Set.copyOf(result);
    }

    static Set<CollectionId> collections(List<UUID> source) {
        var result = new LinkedHashSet<CollectionId>();
        source.forEach(id -> result.add(new CollectionId(id)));
        return Set.copyOf(result);
    }

    static List<ProductId> products(List<UUID> source) {
        return source.stream().map(ProductId::new).toList();
    }

    private static CatalogAttributeValueDto attribute(AttributeValue value) {
        return new CatalogAttributeValueDto(
                        value.code(),
                        value.displayName(),
                        CatalogAttributeValueDto.TypeEnum.valueOf(value.type().name()),
                        value.value(),
                        value.label())
                .colorHex(value.colorHex());
    }

    private static List<UUID> uuids(List<UUID> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }
}
