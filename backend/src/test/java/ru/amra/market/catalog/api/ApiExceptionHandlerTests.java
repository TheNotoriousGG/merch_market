package ru.amra.market.catalog.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import ru.amra.market.catalog.application.AdminCatalogProductNotFoundException;
import ru.amra.market.catalog.application.CatalogCategoryNotFoundException;
import ru.amra.market.catalog.application.CatalogCollectionNotFoundException;
import ru.amra.market.catalog.application.CatalogIdempotencyConflictException;
import ru.amra.market.catalog.application.CatalogProductChildNotFoundException;
import ru.amra.market.catalog.application.CatalogProductNotFoundException;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.StaleCatalogVersionException;
import ru.amra.market.catalog.domain.CategoryInvariant;
import ru.amra.market.catalog.domain.CategoryInvariantViolation;
import ru.amra.market.catalog.domain.CollectionInvariant;
import ru.amra.market.catalog.domain.CollectionInvariantViolation;
import ru.amra.market.catalog.domain.ProductInvariant;
import ru.amra.market.catalog.domain.ProductInvariantViolation;
import ru.amra.market.platform.web.ApiProblemFactory;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler(new ApiProblemFactory());
    private final MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/admin/catalog/test");

    @Test
    void mapsEachCatalogFailureFamilyToAStableCode() {
        assertThat(code(new CatalogCategoryNotFoundException())).isEqualTo("CATEGORY_NOT_FOUND");
        assertThat(code(new CatalogProductChildNotFoundException())).isEqualTo("PRODUCT_CHILD_NOT_FOUND");
        assertThat(code(new AdminCatalogProductNotFoundException())).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(code(new CatalogProductNotFoundException())).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(code(new CatalogCollectionNotFoundException())).isEqualTo("COLLECTION_NOT_FOUND");
        assertThat(code(new StaleCatalogVersionException())).isEqualTo("STALE_RESOURCE_VERSION");
        assertThat(code(new ConcurrentCatalogModificationException("stale"))).isEqualTo("STALE_RESOURCE_VERSION");
        assertThat(code(new CatalogIdempotencyConflictException())).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(code(new CategoryInvariantViolation(CategoryInvariant.CATEGORY_CYCLE, "cycle")))
                .isEqualTo("CATEGORY_CYCLE");
        assertThat(code(new CollectionInvariantViolation(CollectionInvariant.INVALID_ORDER, "order")))
                .isEqualTo("COLLECTION_INVALID_ORDER");
        assertThat(code(new DataIntegrityViolationException("private database detail")))
                .isEqualTo("CATALOG_CONFLICT");
        assertThat(code(new IllegalArgumentException("unsafe caller detail"))).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void specializesProductNamespaceAndIdentityConflicts() {
        assertThat(productCode(ProductInvariant.SLUG_REUSE)).isEqualTo("PRODUCT_SLUG_CONFLICT");
        assertThat(productCode(ProductInvariant.SLUG_NAMESPACE_CONFLICT)).isEqualTo("PRODUCT_SLUG_CONFLICT");
        assertThat(productCode(ProductInvariant.DUPLICATE_SKU)).isEqualTo("SKU_CONFLICT");
        assertThat(productCode(ProductInvariant.DUPLICATE_VARIANT_COMBINATION))
                .isEqualTo("VARIANT_COMBINATION_CONFLICT");
        assertThat(productCode(ProductInvariant.PRODUCT_NOT_PUBLISHABLE)).isEqualTo("PRODUCT_NOT_PUBLISHABLE");
    }

    @Test
    void safelyMapsControllerStatusesWithAndWithoutDomainCauses() {
        assertThat(responseCode(new ResponseStatusException(HttpStatus.BAD_REQUEST)))
                .isEqualTo("REQUEST_REJECTED");
        assertThat(responseCode(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)))
                .isEqualTo("INTERNAL_ERROR");
        assertThat(responseCode(new ResponseStatusException(HttpStatusCode.valueOf(599))))
                .isEqualTo("INTERNAL_ERROR");
        assertThat(responseCode(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "hidden", new CatalogCategoryNotFoundException())))
                .isEqualTo("CATEGORY_NOT_FOUND");

        var unexpected = requireNonNull(
                handler.unexpected(new IOException("private detail"), request).getBody());
        assertThat(unexpected.getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(unexpected.getDetail()).doesNotContain("private detail");
    }

    private String code(RuntimeException exception) {
        return requireNonNull(handler.domainFailure(exception, request).getBody())
                .getCode();
    }

    private String productCode(ProductInvariant invariant) {
        return code(new ProductInvariantViolation(invariant, "safe invariant detail"));
    }

    private String responseCode(ResponseStatusException exception) {
        return requireNonNull(handler.responseStatus(exception, request).getBody())
                .getCode();
    }
}
