package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.amra.market.catalog.application.AdminCatalogProductNotFoundException;
import ru.amra.market.catalog.application.AdminCategoryListCriteria;
import ru.amra.market.catalog.application.AdminCategoryView;
import ru.amra.market.catalog.application.AdminProductListCriteria;
import ru.amra.market.catalog.application.CatalogCategoryNotFoundException;
import ru.amra.market.catalog.application.CatalogCollectionNotFoundException;
import ru.amra.market.catalog.application.CatalogIdempotencyConflictException;
import ru.amra.market.catalog.application.CatalogProductChildNotFoundException;
import ru.amra.market.catalog.application.CatalogVersionEtag;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.CreateCatalogCategoryCommand;
import ru.amra.market.catalog.application.ListAdminCatalogCategories;
import ru.amra.market.catalog.application.ListAdminCatalogProducts;
import ru.amra.market.catalog.application.ManageCatalogCategories;
import ru.amra.market.catalog.application.ManageCatalogCollections;
import ru.amra.market.catalog.application.ManageCatalogProducts;
import ru.amra.market.catalog.application.MediaUploadService;
import ru.amra.market.catalog.application.StaleCatalogVersionException;
import ru.amra.market.catalog.application.UpdateCatalogCategoryCommand;
import ru.amra.market.catalog.application.port.MediaDeliveryUrlProvider;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryInvariantViolation;
import ru.amra.market.catalog.domain.CategoryStatus;
import ru.amra.market.catalog.domain.CollectionId;
import ru.amra.market.catalog.domain.CollectionInvariantViolation;
import ru.amra.market.catalog.domain.CollectionSlug;
import ru.amra.market.catalog.domain.CollectionStatus;
import ru.amra.market.catalog.domain.MediaId;
import ru.amra.market.catalog.domain.ProductContent;
import ru.amra.market.catalog.domain.ProductId;
import ru.amra.market.catalog.domain.ProductInvariantViolation;
import ru.amra.market.catalog.domain.ProductMerchandising;
import ru.amra.market.catalog.domain.ProductPrice;
import ru.amra.market.catalog.domain.ProductSlug;
import ru.amra.market.catalog.domain.Sku;
import ru.amra.market.catalog.domain.VariantId;
import ru.amra.market.catalog.domain.VariantStatus;
import ru.amra.market.platform.generated.api.CatalogAdministrationApi;
import ru.amra.market.platform.generated.model.AdminCategoryDto;
import ru.amra.market.platform.generated.model.AdminCategoryListDto;
import ru.amra.market.platform.generated.model.AdminCollectionDto;
import ru.amra.market.platform.generated.model.AdminMediaDto;
import ru.amra.market.platform.generated.model.AdminProductDto;
import ru.amra.market.platform.generated.model.AdminProductPageDto;
import ru.amra.market.platform.generated.model.AdminProductSummaryDto;
import ru.amra.market.platform.generated.model.AdminVariantDto;
import ru.amra.market.platform.generated.model.CreateCategoryRequestDto;
import ru.amra.market.platform.generated.model.CreateCollectionRequestDto;
import ru.amra.market.platform.generated.model.CreateMediaRequestDto;
import ru.amra.market.platform.generated.model.CreateMediaUploadRequestDto;
import ru.amra.market.platform.generated.model.CreateProductRequestDto;
import ru.amra.market.platform.generated.model.CreateVariantRequestDto;
import ru.amra.market.platform.generated.model.MediaUploadDto;
import ru.amra.market.platform.generated.model.PageMetadataDto;
import ru.amra.market.platform.generated.model.UpdateCategoryRequestDto;
import ru.amra.market.platform.generated.model.UpdateCollectionProductsRequestDto;
import ru.amra.market.platform.generated.model.UpdateCollectionRequestDto;
import ru.amra.market.platform.generated.model.UpdateMediaRequestDto;
import ru.amra.market.platform.generated.model.UpdateProductRequestDto;
import ru.amra.market.platform.generated.model.UpdateVariantRequestDto;

/** Generated-contract HTTP adapter for complete catalog administration. */
@RestController
public final class CatalogAdministrationController implements CatalogAdministrationApi {

    private final ManageCatalogCategories categories;
    private final ManageCatalogProducts products;
    private final ManageCatalogCollections collections;
    private final ListAdminCatalogCategories categoryList;
    private final ListAdminCatalogProducts productList;
    private final MediaUploadService mediaUploads;
    private final MediaDeliveryUrlProvider mediaUrls;

    public CatalogAdministrationController(
            ManageCatalogCategories categories,
            ManageCatalogProducts products,
            ManageCatalogCollections collections,
            ListAdminCatalogCategories categoryList,
            ListAdminCatalogProducts productList,
            MediaUploadService mediaUploads,
            MediaDeliveryUrlProvider mediaUrls) {
        this.categories = categories;
        this.products = products;
        this.collections = collections;
        this.categoryList = categoryList;
        this.productList = productList;
        this.mediaUploads = mediaUploads;
        this.mediaUrls = mediaUrls;
    }

    @Override
    public ResponseEntity<MediaUploadDto> createCatalogProductMediaUpload(
            UUID resourceId, String csrf, CreateMediaUploadRequestDto request) {
        return translate(() -> {
            products.get(new ProductId(resourceId));
            var upload = mediaUploads.prepare(
                    new ProductId(resourceId),
                    requireNonNull(request.getFileName()),
                    requireNonNull(request.getContentType()).getValue(),
                    requireNonNull(request.getSize()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new MediaUploadDto(
                            upload.objectKey(),
                            upload.uploadUrl(),
                            upload.expiresAt(),
                            upload.contentType(),
                            upload.maxSize()));
        });
    }

    @Override
    public ResponseEntity<AdminCategoryListDto> listAdminCatalogCategories(
            @Nullable String q, @Nullable String status) {
        return translate(() -> {
            var criteria = new AdminCategoryListCriteria(
                    q, status == null ? null : CategoryStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT)));
            return ResponseEntity.ok(new AdminCategoryListDto(categoryList.execute(criteria).stream()
                    .map(CatalogAdministrationController::toDto)
                    .toList()));
        });
    }

    @Override
    public ResponseEntity<AdminProductPageDto> listAdminCatalogProducts(
            @Nullable String q,
            @Nullable String status,
            @Nullable UUID categoryId,
            Integer page,
            Integer size,
            String sort) {
        return translate(() -> {
            var criteria = new AdminProductListCriteria(
                    q,
                    status == null
                            ? null
                            : ru.amra.market.catalog.domain.ProductStatus.valueOf(
                                    status.toUpperCase(java.util.Locale.ROOT)),
                    categoryId == null ? null : new CategoryId(categoryId),
                    page,
                    size,
                    AdminProductListCriteria.Sort.valueOf(sort.toUpperCase(java.util.Locale.ROOT)));
            var result = productList.execute(criteria);
            var items = result.items().stream()
                    .map(item -> new AdminProductSummaryDto(
                            item.id(),
                            item.slug(),
                            item.name(),
                            AdminProductSummaryDto.StatusEnum.valueOf(
                                    item.status().name()),
                            item.primaryCategoryId(),
                            item.variantCount(),
                            item.mediaCount(),
                            item.hasPrimaryMedia(),
                            item.version(),
                            item.updatedAt()))
                    .toList();
            return ResponseEntity.ok(new AdminProductPageDto(
                    items,
                    new PageMetadataDto(result.page(), result.size(), result.totalElements(), result.totalPages())));
        });
    }

    @Override
    public ResponseEntity<AdminCategoryDto> createCatalogCategory(
            String idempotencyKey, String csrf, CreateCategoryRequestDto request) {
        return translate(() -> {
            var parentId = request.getParentId() == null ? null : new CategoryId(request.getParentId());
            var created = categories.create(new CreateCatalogCategoryCommand(
                    parentId,
                    requireNonNull(request.getSlug()),
                    requireNonNull(request.getName()),
                    requireNonNull(request.getDisplayOrder()),
                    actorScope(),
                    idempotencyKey));
            return ResponseEntity.created(
                            location(CatalogAdministrationApi.PATH_GET_ADMIN_CATALOG_CATEGORY, created.id()))
                    .eTag(created.etag())
                    .body(toDto(created));
        });
    }

    @Override
    public ResponseEntity<AdminCategoryDto> getAdminCatalogCategory(UUID resourceId) {
        return translate(() -> {
            var category = categories.get(new CategoryId(resourceId));
            return ResponseEntity.ok().eTag(category.etag()).body(toDto(category));
        });
    }

    @Override
    public ResponseEntity<AdminCategoryDto> updateCatalogCategory(
            UUID resourceId, String ifMatch, String csrf, UpdateCategoryRequestDto request) {
        return translate(() -> {
            var clearParent = Boolean.TRUE.equals(request.getClearParent());
            if (clearParent && request.getParentId() != null) {
                throw new IllegalArgumentException("clearParent and parentId are mutually exclusive");
            }
            var parentSpecified = clearParent || request.getParentId() != null;
            var parentId = request.getParentId() == null ? null : new CategoryId(request.getParentId());
            var status = request.getStatus() == null
                    ? null
                    : CategoryStatus.valueOf(request.getStatus().name());
            var updated = categories.update(new UpdateCatalogCategoryCommand(
                    new CategoryId(resourceId),
                    CatalogVersionEtag.parse(ifMatch),
                    parentSpecified,
                    parentId,
                    request.getSlug(),
                    request.getName(),
                    request.getDisplayOrder(),
                    status));
            return ResponseEntity.ok().eTag(updated.etag()).body(toDto(updated));
        });
    }

    @Override
    public ResponseEntity<Void> deleteCatalogCategory(UUID resourceId, String ifMatch, String csrf) {
        return translate(() -> {
            categories.delete(new CategoryId(resourceId), CatalogVersionEtag.parse(ifMatch));
            return ResponseEntity.noContent().build();
        });
    }

    @Override
    public ResponseEntity<AdminProductDto> createCatalogProduct(
            String idempotencyKey, String csrf, CreateProductRequestDto request) {
        return translate(() -> {
            var created = products.create(new ManageCatalogProducts.CreateCommand(
                    new ProductSlug(requireNonNull(request.getSlug())),
                    new ProductContent(
                            requireNonNull(request.getName()),
                            requireNonNull(request.getShortDescription()),
                            requireNonNull(request.getDescription())),
                    request.getPriceMinor() == null ? null : new ProductPrice(request.getPriceMinor()),
                    new CategoryId(requireNonNull(request.getPrimaryCategoryId())),
                    CatalogAdministrationDtoMapper.categories(requireNonNull(request.getCategoryIds())),
                    CatalogAdministrationDtoMapper.collections(request.getCollectionIds()),
                    CatalogAdministrationDtoMapper.attributes(request.getCharacteristics(), false),
                    actorScope(),
                    idempotencyKey));
            return ResponseEntity.created(location(
                            CatalogAdministrationApi.PATH_GET_ADMIN_CATALOG_PRODUCT,
                            created.product().id().value()))
                    .eTag(created.etag())
                    .body(CatalogAdministrationDtoMapper.product(created, mediaUrls));
        });
    }

    @Override
    public ResponseEntity<AdminProductDto> getAdminCatalogProduct(UUID resourceId) {
        return translate(() -> {
            var product = products.get(new ProductId(resourceId));
            return ResponseEntity.ok()
                    .eTag(product.etag())
                    .body(CatalogAdministrationDtoMapper.product(product, mediaUrls));
        });
    }

    @Override
    public ResponseEntity<AdminProductDto> updateCatalogProduct(
            UUID resourceId, String ifMatch, String csrf, UpdateProductRequestDto request) {
        return translate(() -> {
            var updated = products.update(new ManageCatalogProducts.UpdateCommand(
                    new ProductId(resourceId),
                    CatalogVersionEtag.parse(ifMatch),
                    request.getSlug() == null ? null : new ProductSlug(request.getSlug()),
                    request.getName(),
                    request.getShortDescription(),
                    request.getDescription(),
                    request.getPriceMinor() == null ? null : new ProductPrice(request.getPriceMinor()),
                    request.getPrimaryCategoryId() == null ? null : new CategoryId(request.getPrimaryCategoryId()),
                    request.getCategoryIds() == null
                            ? null
                            : CatalogAdministrationDtoMapper.categories(request.getCategoryIds()),
                    request.getCollectionIds() == null
                            ? null
                            : CatalogAdministrationDtoMapper.collections(request.getCollectionIds()),
                    request.getCharacteristics() == null
                            ? null
                            : CatalogAdministrationDtoMapper.attributes(request.getCharacteristics(), false),
                    request.getMerchandising() == null
                            ? null
                            : new ProductMerchandising(
                                    requireNonNull(request.getMerchandising().getNewArrival()),
                                    request.getMerchandising().getNewUntil(),
                                    requireNonNull(request.getMerchandising().getOnSale()),
                                    request.getMerchandising().getSalePercent(),
                                    requireNonNull(request.getMerchandising().getFeatured()))));
            return ResponseEntity.ok()
                    .eTag(updated.etag())
                    .body(CatalogAdministrationDtoMapper.product(updated, mediaUrls));
        });
    }

    @Override
    public ResponseEntity<AdminProductDto> transitionCatalogProduct(
            UUID resourceId, String transition, String ifMatch, String idempotencyKey, String csrf) {
        return translate(() -> {
            var changed = products.transition(new ManageCatalogProducts.TransitionCommand(
                    new ProductId(resourceId),
                    CatalogVersionEtag.parse(ifMatch),
                    switch (transition) {
                        case "publish" -> ManageCatalogProducts.Transition.PUBLISH;
                        case "archive" -> ManageCatalogProducts.Transition.ARCHIVE;
                        case "restore" -> ManageCatalogProducts.Transition.RESTORE;
                        default -> throw new IllegalArgumentException("Unsupported product transition");
                    },
                    actorScope(),
                    idempotencyKey));
            return ResponseEntity.ok()
                    .eTag(changed.etag())
                    .body(CatalogAdministrationDtoMapper.product(changed, mediaUrls));
        });
    }

    @Override
    public ResponseEntity<Void> deleteCatalogProduct(UUID resourceId, String ifMatch, String csrf) {
        return translate(() -> {
            products.delete(new ProductId(resourceId), CatalogVersionEtag.parse(ifMatch));
            return ResponseEntity.noContent().build();
        });
    }

    @Override
    public ResponseEntity<AdminVariantDto> createCatalogProductVariant(
            UUID resourceId, String ifMatch, String idempotencyKey, String csrf, CreateVariantRequestDto request) {
        return translate(() -> {
            var created = products.createVariant(new ManageCatalogProducts.CreateVariantCommand(
                    new ProductId(resourceId),
                    CatalogVersionEtag.parse(ifMatch),
                    new Sku(requireNonNull(request.getSku())),
                    requireNonNull(request.getLabel()),
                    requireNonNull(request.getDisplayOrder()),
                    CatalogAdministrationDtoMapper.attributes(requireNonNull(request.getAttributes()), true),
                    actorScope(),
                    idempotencyKey));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .eTag(created.productEtag())
                    .body(CatalogAdministrationDtoMapper.variant(created));
        });
    }

    @Override
    public ResponseEntity<AdminVariantDto> updateCatalogProductVariant(
            UUID resourceId, UUID variantId, String ifMatch, String csrf, UpdateVariantRequestDto request) {
        return translate(() -> {
            var updated = products.updateVariant(new ManageCatalogProducts.UpdateVariantCommand(
                    new ProductId(resourceId),
                    new VariantId(variantId),
                    CatalogVersionEtag.parse(ifMatch),
                    request.getLabel(),
                    request.getStatus() == null
                            ? null
                            : VariantStatus.valueOf(request.getStatus().name()),
                    request.getDisplayOrder(),
                    request.getAttributes() == null
                            ? null
                            : CatalogAdministrationDtoMapper.attributes(request.getAttributes(), true)));
            return ResponseEntity.ok()
                    .eTag(updated.productEtag())
                    .body(CatalogAdministrationDtoMapper.variant(updated));
        });
    }

    @Override
    public ResponseEntity<AdminMediaDto> createCatalogProductMedia(
            UUID resourceId, String ifMatch, String idempotencyKey, String csrf, CreateMediaRequestDto request) {
        return translate(() -> {
            if (requireNonNull(request.getObjectKey()).startsWith("products/" + resourceId + "/uploads/")) {
                mediaUploads.verify(
                        new ProductId(resourceId), request.getObjectKey(), requireNonNull(request.getContentType()));
            }
            var created = products.createMedia(new ManageCatalogProducts.CreateMediaCommand(
                    new ProductId(resourceId),
                    CatalogVersionEtag.parse(ifMatch),
                    request.getVariantId() == null ? null : new VariantId(request.getVariantId()),
                    requireNonNull(request.getObjectKey()),
                    requireNonNull(request.getContentType()),
                    requireNonNull(request.getWidth()),
                    requireNonNull(request.getHeight()),
                    requireNonNull(request.getAlt()),
                    requireNonNull(request.getDisplayOrder()),
                    requireNonNull(request.getPrimary()),
                    actorScope(),
                    idempotencyKey));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .eTag(created.productEtag())
                    .body(CatalogAdministrationDtoMapper.media(created, mediaUrls));
        });
    }

    @Override
    public ResponseEntity<AdminMediaDto> updateCatalogProductMedia(
            UUID resourceId, UUID mediaId, String ifMatch, String csrf, UpdateMediaRequestDto request) {
        return translate(() -> {
            var clearVariant = Boolean.TRUE.equals(request.getClearVariant());
            if (clearVariant && request.getVariantId() != null) {
                throw new IllegalArgumentException("clearVariant and variantId are mutually exclusive");
            }
            var updated = products.updateMedia(new ManageCatalogProducts.UpdateMediaCommand(
                    new ProductId(resourceId),
                    new MediaId(mediaId),
                    CatalogVersionEtag.parse(ifMatch),
                    clearVariant || request.getVariantId() != null,
                    request.getVariantId() == null ? null : new VariantId(request.getVariantId()),
                    request.getAlt(),
                    request.getDisplayOrder(),
                    request.getPrimary()));
            return ResponseEntity.ok()
                    .eTag(updated.productEtag())
                    .body(CatalogAdministrationDtoMapper.media(updated, mediaUrls));
        });
    }

    @Override
    public ResponseEntity<Void> deleteCatalogProductMedia(UUID resourceId, UUID mediaId, String ifMatch, String csrf) {
        return translate(() -> {
            products.deleteMedia(new ProductId(resourceId), new MediaId(mediaId), CatalogVersionEtag.parse(ifMatch));
            return ResponseEntity.noContent().build();
        });
    }

    @Override
    public ResponseEntity<AdminCollectionDto> createCatalogCollection(
            String idempotencyKey, String csrf, CreateCollectionRequestDto request) {
        return translate(() -> {
            var created = collections.create(new ManageCatalogCollections.CreateCommand(
                    new CollectionSlug(requireNonNull(request.getSlug())),
                    requireNonNull(request.getName()),
                    requireNonNull(request.getDescription()),
                    requireNonNull(request.getDisplayOrder()),
                    actorScope(),
                    idempotencyKey));
            return ResponseEntity.created(location(
                            CatalogAdministrationApi.PATH_GET_ADMIN_CATALOG_COLLECTION,
                            created.collection().id().value()))
                    .eTag(created.etag())
                    .body(CatalogAdministrationDtoMapper.collection(created));
        });
    }

    @Override
    public ResponseEntity<AdminCollectionDto> getAdminCatalogCollection(UUID resourceId) {
        return translate(() -> {
            var collection = collections.get(new CollectionId(resourceId));
            return ResponseEntity.ok()
                    .eTag(collection.etag())
                    .body(CatalogAdministrationDtoMapper.collection(collection));
        });
    }

    @Override
    public ResponseEntity<AdminCollectionDto> updateCatalogCollection(
            UUID resourceId, String ifMatch, String csrf, UpdateCollectionRequestDto request) {
        return translate(() -> {
            var updated = collections.update(new ManageCatalogCollections.UpdateCommand(
                    new CollectionId(resourceId),
                    CatalogVersionEtag.parse(ifMatch),
                    request.getSlug() == null ? null : new CollectionSlug(request.getSlug()),
                    request.getName(),
                    request.getDescription(),
                    request.getStatus() == null
                            ? null
                            : CollectionStatus.valueOf(request.getStatus().name()),
                    request.getDisplayOrder()));
            return ResponseEntity.ok().eTag(updated.etag()).body(CatalogAdministrationDtoMapper.collection(updated));
        });
    }

    @Override
    public ResponseEntity<AdminCollectionDto> updateCatalogCollectionProducts(
            UUID resourceId,
            String ifMatch,
            String idempotencyKey,
            String csrf,
            UpdateCollectionProductsRequestDto request) {
        return translate(() -> {
            var updated = collections.replaceProducts(new ManageCatalogCollections.ReplaceProductsCommand(
                    new CollectionId(resourceId),
                    CatalogVersionEtag.parse(ifMatch),
                    CatalogAdministrationDtoMapper.products(requireNonNull(request.getProductIds())),
                    actorScope(),
                    idempotencyKey));
            return ResponseEntity.ok().eTag(updated.etag()).body(CatalogAdministrationDtoMapper.collection(updated));
        });
    }

    private static AdminCategoryDto toDto(AdminCategoryView category) {
        var archived = category.status() == CategoryStatus.ARCHIVED;
        return new AdminCategoryDto(
                        category.id(),
                        category.slug(),
                        category.name(),
                        category.displayOrder(),
                        archived
                                ? AdminCategoryDto.StatusEnum.HIDDEN
                                : AdminCategoryDto.StatusEnum.valueOf(
                                        category.status().name()),
                        category.version(),
                        category.updatedAt())
                .parentId(category.parentId())
                .archived(archived);
    }

    private static URI location(String path, UUID id) {
        return URI.create("/api/v1" + path.replace("{resourceId}", id.toString()));
    }

    private static String actorScope() {
        var authentication = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
        return authentication.getName();
    }

    private static <T> ResponseEntity<T> translate(Supplier<ResponseEntity<T>> action) {
        try {
            return action.get();
        } catch (CatalogCategoryNotFoundException
                | AdminCatalogProductNotFoundException
                | CatalogProductChildNotFoundException
                | CatalogCollectionNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog resource not found", exception);
        } catch (StaleCatalogVersionException | ConcurrentCatalogModificationException exception) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Catalog version is stale", exception);
        } catch (CategoryInvariantViolation
                | ProductInvariantViolation
                | CollectionInvariantViolation
                | CatalogIdempotencyConflictException
                | DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Catalog command conflict", exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog command", exception);
        }
    }
}
