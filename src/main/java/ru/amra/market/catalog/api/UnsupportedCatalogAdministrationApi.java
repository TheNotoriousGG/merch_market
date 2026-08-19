package ru.amra.market.catalog.api;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.amra.market.platform.generated.api.CatalogAdministrationApi;
import ru.amra.market.platform.generated.model.AdminCategoryDto;
import ru.amra.market.platform.generated.model.AdminCollectionDto;
import ru.amra.market.platform.generated.model.AdminMediaDto;
import ru.amra.market.platform.generated.model.AdminProductDto;
import ru.amra.market.platform.generated.model.AdminVariantDto;
import ru.amra.market.platform.generated.model.CreateCategoryRequestDto;
import ru.amra.market.platform.generated.model.CreateCollectionRequestDto;
import ru.amra.market.platform.generated.model.CreateMediaRequestDto;
import ru.amra.market.platform.generated.model.CreateProductRequestDto;
import ru.amra.market.platform.generated.model.CreateVariantRequestDto;
import ru.amra.market.platform.generated.model.UpdateCategoryRequestDto;
import ru.amra.market.platform.generated.model.UpdateCollectionProductsRequestDto;
import ru.amra.market.platform.generated.model.UpdateCollectionRequestDto;
import ru.amra.market.platform.generated.model.UpdateMediaRequestDto;
import ru.amra.market.platform.generated.model.UpdateProductRequestDto;
import ru.amra.market.platform.generated.model.UpdateVariantRequestDto;

/** Temporary defaults for generated admin operations delivered by later catalog blocks. */
abstract class UnsupportedCatalogAdministrationApi implements CatalogAdministrationApi {

    @Override
    public ResponseEntity<AdminCategoryDto> createCatalogCategory(
            String idempotencyKey, String csrf, CreateCategoryRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminCollectionDto> createCatalogCollection(
            String idempotencyKey, String csrf, CreateCollectionRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminProductDto> createCatalogProduct(
            String idempotencyKey, String csrf, CreateProductRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminMediaDto> createCatalogProductMedia(
            UUID resourceId, String ifMatch, String idempotencyKey, String csrf, CreateMediaRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminVariantDto> createCatalogProductVariant(
            UUID resourceId, String ifMatch, String idempotencyKey, String csrf, CreateVariantRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminCategoryDto> getAdminCatalogCategory(UUID resourceId) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminCollectionDto> getAdminCatalogCollection(UUID resourceId) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminProductDto> getAdminCatalogProduct(UUID resourceId) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminProductDto> transitionCatalogProduct(
            UUID resourceId, String transition, String ifMatch, String idempotencyKey, String csrf) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminCategoryDto> updateCatalogCategory(
            UUID resourceId, String ifMatch, String csrf, UpdateCategoryRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminCollectionDto> updateCatalogCollection(
            UUID resourceId, String ifMatch, String csrf, UpdateCollectionRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminCollectionDto> updateCatalogCollectionProducts(
            UUID resourceId,
            String ifMatch,
            String idempotencyKey,
            String csrf,
            UpdateCollectionProductsRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminProductDto> updateCatalogProduct(
            UUID resourceId, String ifMatch, String csrf, UpdateProductRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminMediaDto> updateCatalogProductMedia(
            UUID resourceId, UUID mediaId, String ifMatch, String csrf, UpdateMediaRequestDto request) {
        return notImplemented();
    }

    @Override
    public ResponseEntity<AdminVariantDto> updateCatalogProductVariant(
            UUID resourceId, UUID variantId, String ifMatch, String csrf, UpdateVariantRequestDto request) {
        return notImplemented();
    }

    private static <T> ResponseEntity<T> notImplemented() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
