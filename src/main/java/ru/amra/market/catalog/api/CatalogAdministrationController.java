package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.amra.market.catalog.application.AdminCategoryView;
import ru.amra.market.catalog.application.CatalogCategoryNotFoundException;
import ru.amra.market.catalog.application.CatalogIdempotencyConflictException;
import ru.amra.market.catalog.application.CatalogVersionEtag;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.CreateCatalogCategoryCommand;
import ru.amra.market.catalog.application.ManageCatalogCategories;
import ru.amra.market.catalog.application.StaleCatalogVersionException;
import ru.amra.market.catalog.application.UpdateCatalogCategoryCommand;
import ru.amra.market.catalog.domain.CategoryId;
import ru.amra.market.catalog.domain.CategoryInvariantViolation;
import ru.amra.market.catalog.domain.CategoryStatus;
import ru.amra.market.platform.generated.api.CatalogAdministrationApi;
import ru.amra.market.platform.generated.model.AdminCategoryDto;
import ru.amra.market.platform.generated.model.CreateCategoryRequestDto;
import ru.amra.market.platform.generated.model.UpdateCategoryRequestDto;

/** Generated-contract HTTP adapter for catalog administration. */
@RestController
public final class CatalogAdministrationController extends UnsupportedCatalogAdministrationApi {

    private final ManageCatalogCategories categories;

    public CatalogAdministrationController(ManageCatalogCategories categories) {
        this.categories = categories;
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
            return ResponseEntity.created(URI.create("/api/v1"
                            + CatalogAdministrationApi.PATH_GET_ADMIN_CATALOG_CATEGORY.replace(
                                    "{resourceId}", created.id().toString())))
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

    private static AdminCategoryDto toDto(AdminCategoryView category) {
        return new AdminCategoryDto(
                        category.id(),
                        category.slug(),
                        category.name(),
                        category.displayOrder(),
                        AdminCategoryDto.StatusEnum.valueOf(category.status().name()),
                        category.version(),
                        category.updatedAt())
                .parentId(category.parentId());
    }

    private static String actorScope() {
        var authentication = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
        return authentication.getName();
    }

    private static <T> ResponseEntity<T> translate(Supplier<ResponseEntity<T>> action) {
        try {
            return action.get();
        } catch (CatalogCategoryNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog category not found", exception);
        } catch (StaleCatalogVersionException | ConcurrentCatalogModificationException exception) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Catalog version is stale", exception);
        } catch (CategoryInvariantViolation
                | CatalogIdempotencyConflictException
                | DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Catalog category conflict", exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid catalog category command", exception);
        }
    }
}
