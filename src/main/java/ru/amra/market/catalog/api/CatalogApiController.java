package ru.amra.market.catalog.api;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.amra.market.catalog.application.CatalogCategoryTree;
import ru.amra.market.catalog.application.CatalogProductListCriteria;
import ru.amra.market.catalog.application.CatalogProductPage;
import ru.amra.market.catalog.application.CatalogProductSort;
import ru.amra.market.catalog.application.GetCatalogCategoryTree;
import ru.amra.market.catalog.application.ListCatalogProducts;
import ru.amra.market.platform.generated.api.CatalogApi;
import ru.amra.market.platform.generated.model.CatalogAttributeValueDto;
import ru.amra.market.platform.generated.model.CatalogCategoryNodeDto;
import ru.amra.market.platform.generated.model.CatalogCategoryTreeDto;
import ru.amra.market.platform.generated.model.CatalogMediaDto;
import ru.amra.market.platform.generated.model.CatalogProductDetailDto;
import ru.amra.market.platform.generated.model.CatalogProductPageDto;
import ru.amra.market.platform.generated.model.CatalogProductSummaryDto;
import ru.amra.market.platform.generated.model.CatalogVariantOptionDto;
import ru.amra.market.platform.generated.model.PageMetadataDto;

/** HTTP adapter for public catalog reads defined by the generated OpenAPI interface. */
@RestController
public final class CatalogApiController implements CatalogApi {

    private static final CacheControl NAVIGATION_CACHE = CacheControl.maxAge(Duration.ofMinutes(1))
            .staleWhileRevalidate(Duration.ofMinutes(5))
            .cachePublic();
    private static final CacheControl PRODUCT_LIST_CACHE = CacheControl.maxAge(Duration.ofSeconds(30))
            .staleWhileRevalidate(Duration.ofMinutes(2))
            .cachePublic();

    private final GetCatalogCategoryTree getCategoryTree;
    private final ListCatalogProducts listProducts;

    public CatalogApiController(GetCatalogCategoryTree getCategoryTree, ListCatalogProducts listProducts) {
        this.getCategoryTree = getCategoryTree;
        this.listProducts = listProducts;
    }

    @Override
    public ResponseEntity<CatalogCategoryTreeDto> getCatalogCategories() {
        var result = getCategoryTree.execute();
        var body = new CatalogCategoryTreeDto(
                result.categories().stream().map(CatalogApiController::toDto).toList());
        return ResponseEntity.ok()
                .cacheControl(NAVIGATION_CACHE)
                .eTag(result.etag())
                .body(body);
    }

    @Override
    public ResponseEntity<CatalogProductDetailDto> getCatalogProduct(String slug) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<CatalogProductPageDto> getCatalogProducts(
            Integer page,
            Integer size,
            @Nullable String category,
            @Nullable String collection,
            @Nullable String q,
            Boolean onlyNew,
            @Nullable List<String> sizeValue,
            @Nullable List<String> colorValue,
            String sort) {
        try {
            var criteria = new CatalogProductListCriteria(
                    page,
                    size,
                    category,
                    collection,
                    q,
                    onlyNew,
                    sizeValue == null ? List.of() : sizeValue,
                    colorValue == null ? List.of() : colorValue,
                    CatalogProductSort.valueOf(sort));
            var result = listProducts.execute(criteria);
            var body = new CatalogProductPageDto(
                    result.items().stream().map(CatalogApiController::toDto).toList(),
                    new PageMetadataDto(
                            result.page().page(),
                            result.page().size(),
                            result.page().totalElements(),
                            result.page().totalPages()));
            return ResponseEntity.ok()
                    .cacheControl(PRODUCT_LIST_CACHE)
                    .eTag(result.etag())
                    .body(body);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog filter", exception);
        }
    }

    private static CatalogCategoryNodeDto toDto(CatalogCategoryTree.Node node) {
        return new CatalogCategoryNodeDto(
                node.id(),
                node.slug(),
                node.name(),
                node.displayOrder(),
                node.children().stream().map(CatalogApiController::toDto).toList());
    }

    private static CatalogProductSummaryDto toDto(CatalogProductPage.Item item) {
        var media = item.primaryMedia();
        var mediaDto = new CatalogMediaDto(
                media.id(),
                CatalogMediaDto.TypeEnum.IMAGE,
                media.url(),
                media.alt(),
                media.width(),
                media.height(),
                media.displayOrder());
        return new CatalogProductSummaryDto(
                item.id(),
                item.slug(),
                item.name(),
                item.shortDescription(),
                mediaDto,
                item.publishedAt(),
                item.variantOptions().stream().map(CatalogApiController::toDto).toList());
    }

    private static CatalogVariantOptionDto toDto(CatalogProductPage.VariantOption option) {
        return new CatalogVariantOptionDto(
                option.definitionCode(),
                option.definitionName(),
                CatalogVariantOptionDto.TypeEnum.valueOf(option.type().name()),
                option.values().stream().map(CatalogApiController::toDto).toList());
    }

    private static CatalogAttributeValueDto toDto(CatalogProductPage.AttributeValue value) {
        return new CatalogAttributeValueDto(
                        value.definitionCode(),
                        value.definitionName(),
                        CatalogAttributeValueDto.TypeEnum.valueOf(value.type().name()),
                        value.valueCode(),
                        value.label())
                .colorHex(value.colorHex());
    }
}
