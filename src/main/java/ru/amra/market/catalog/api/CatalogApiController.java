package ru.amra.market.catalog.api;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.catalog.application.CatalogCategoryTree;
import ru.amra.market.catalog.application.GetCatalogCategoryTree;
import ru.amra.market.platform.generated.api.CatalogApi;
import ru.amra.market.platform.generated.model.CatalogCategoryNodeDto;
import ru.amra.market.platform.generated.model.CatalogCategoryTreeDto;
import ru.amra.market.platform.generated.model.CatalogProductDetailDto;
import ru.amra.market.platform.generated.model.CatalogProductPageDto;

/** HTTP adapter for public catalog reads defined by the generated OpenAPI interface. */
@RestController
public final class CatalogApiController implements CatalogApi {

    private static final CacheControl NAVIGATION_CACHE = CacheControl.maxAge(Duration.ofMinutes(1))
            .staleWhileRevalidate(Duration.ofMinutes(5))
            .cachePublic();

    private final GetCatalogCategoryTree getCategoryTree;

    public CatalogApiController(GetCatalogCategoryTree getCategoryTree) {
        this.getCategoryTree = getCategoryTree;
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
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private static CatalogCategoryNodeDto toDto(CatalogCategoryTree.Node node) {
        return new CatalogCategoryNodeDto(
                node.id(),
                node.slug(),
                node.name(),
                node.displayOrder(),
                node.children().stream().map(CatalogApiController::toDto).toList());
    }
}
