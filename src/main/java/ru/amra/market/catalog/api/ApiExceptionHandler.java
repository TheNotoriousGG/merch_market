package ru.amra.market.catalog.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.amra.market.catalog.application.AdminCatalogProductNotFoundException;
import ru.amra.market.catalog.application.CatalogCategoryNotFoundException;
import ru.amra.market.catalog.application.CatalogCollectionNotFoundException;
import ru.amra.market.catalog.application.CatalogIdempotencyConflictException;
import ru.amra.market.catalog.application.CatalogProductChildNotFoundException;
import ru.amra.market.catalog.application.CatalogProductNotFoundException;
import ru.amra.market.catalog.application.ConcurrentCatalogModificationException;
import ru.amra.market.catalog.application.StaleCatalogVersionException;
import ru.amra.market.catalog.application.StorefrontBannerNotFoundException;
import ru.amra.market.catalog.domain.CategoryInvariantViolation;
import ru.amra.market.catalog.domain.CollectionInvariantViolation;
import ru.amra.market.catalog.domain.ProductInvariantViolation;
import ru.amra.market.platform.generated.model.ProblemDetailsDto;
import ru.amra.market.platform.generated.model.ValidationViolationDto;
import ru.amra.market.platform.web.ApiProblemFactory;

/** Converts transport and domain failures into stable, non-sensitive RFC 9457 responses. */
@RestControllerAdvice
public final class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ApiProblemFactory problems;

    public ApiExceptionHandler(ApiProblemFactory problems) {
        this.problems = problems;
    }

    /** Reports omitted optimistic-concurrency preconditions distinctly from malformed input. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ProblemDetailsDto> missingHeader(
            MissingRequestHeaderException exception, HttpServletRequest request) {
        if ("If-Match".equalsIgnoreCase(exception.getHeaderName())) {
            return response(
                    new Descriptor(
                            HttpStatus.PRECONDITION_REQUIRED,
                            "PRECONDITION_REQUIRED",
                            "A resource version is required",
                            "Supply the current strong ETag in the If-Match header."),
                    request,
                    List.of());
        }
        return response(invalidRequest("A required request header is missing."), request, List.of());
    }

    /** Reports generated-contract bean validation failures with stable field locations. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetailsDto> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        var violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationViolationDto(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        normalizeValidationCode(error.getCode() == null ? "INVALID" : error.getCode())))
                .toList();
        return response(
                new Descriptor(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        "One or more request values are invalid."),
                request,
                violations);
    }

    /** Reports validation performed against generated interface method parameters. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetailsDto> methodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        var violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> {
                    var parameter = result.getMethodParameter();
                    var name = parameter.getParameterName();
                    return new ValidationViolationDto(
                            name == null ? "argument" + parameter.getParameterIndex() : name, validationCode(error));
                }))
                .toList();
        return response(
                new Descriptor(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "Request validation failed",
                        "One or more request values are invalid."),
                request,
                violations);
    }

    /** Reports malformed JSON and incompatible request parameter types. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetailsDto> malformedRequest(Exception exception, HttpServletRequest request) {
        return response(invalidRequest("The request payload or parameter format is invalid."), request, List.of());
    }

    /** Keeps unknown routes in the same safe problem format without converting them to server failures. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetailsDto> routeNotFound(NoResourceFoundException exception, HttpServletRequest request) {
        return response(
                new Descriptor(
                        HttpStatus.NOT_FOUND,
                        "RESOURCE_NOT_FOUND",
                        "Resource not found",
                        "The requested API resource does not exist."),
                request,
                List.of());
    }

    /** Preserves controller status intent while deriving the stable code from the domain cause. */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetailsDto> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        var cause = exception.getCause();
        if (cause != null) {
            return response(describe(cause), request, List.of());
        }
        var status = HttpStatus.resolve(exception.getStatusCode().value());
        var resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        return response(
                new Descriptor(
                        resolved,
                        resolved.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_REJECTED",
                        resolved.is5xxServerError() ? "Internal server error" : "Request rejected",
                        resolved.is5xxServerError()
                                ? "The request could not be completed."
                                : "The request is not valid for this resource."),
                request,
                List.of());
    }

    /** Maps uncaught domain failures without exposing database or implementation diagnostics. */
    @ExceptionHandler({
        CatalogCategoryNotFoundException.class,
        AdminCatalogProductNotFoundException.class,
        CatalogProductNotFoundException.class,
        CatalogProductChildNotFoundException.class,
        CatalogCollectionNotFoundException.class,
        StorefrontBannerNotFoundException.class,
        StaleCatalogVersionException.class,
        ConcurrentCatalogModificationException.class,
        CatalogIdempotencyConflictException.class,
        CategoryInvariantViolation.class,
        ProductInvariantViolation.class,
        CollectionInvariantViolation.class,
        DataIntegrityViolationException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetailsDto> domainFailure(RuntimeException exception, HttpServletRequest request) {
        return response(describe(exception), request, List.of());
    }

    /** Produces a safe terminal response for unexpected failures. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetailsDto> unexpected(Exception exception, HttpServletRequest request) {
        LOG.error("Unexpected catalog API failure for {}", request.getRequestURI(), exception);
        return response(
                new Descriptor(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "Internal server error",
                        "The request could not be completed."),
                request,
                List.of());
    }

    private ResponseEntity<ProblemDetailsDto> response(
            Descriptor descriptor, HttpServletRequest request, List<ValidationViolationDto> violations) {
        var body = problems.create(
                descriptor.status(), descriptor.code(), descriptor.title(), descriptor.detail(), request, violations);
        return ResponseEntity.status(descriptor.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static Descriptor describe(Throwable exception) {
        return switch (exception) {
            case CatalogCategoryNotFoundException ignored ->
                notFound("CATEGORY_NOT_FOUND", "Catalog category was not found.");
            case CatalogProductChildNotFoundException ignored ->
                notFound("PRODUCT_CHILD_NOT_FOUND", "Catalog product child was not found.");
            case AdminCatalogProductNotFoundException ignored ->
                notFound("PRODUCT_NOT_FOUND", "Catalog product was not found.");
            case CatalogProductNotFoundException ignored ->
                notFound("PRODUCT_NOT_FOUND", "Catalog product was not found.");
            case CatalogCollectionNotFoundException ignored ->
                notFound("COLLECTION_NOT_FOUND", "Catalog collection was not found.");
            case StorefrontBannerNotFoundException ignored ->
                notFound("STOREFRONT_BANNER_NOT_FOUND", "Storefront banner was not found.");
            case StaleCatalogVersionException ignored -> staleVersion();
            case ConcurrentCatalogModificationException ignored -> staleVersion();
            case CatalogIdempotencyConflictException ignored ->
                conflict("IDEMPOTENCY_KEY_REUSED", "The idempotency key belongs to a different command.");
            case CategoryInvariantViolation violation ->
                conflict(violation.invariant().name(), "The category change violates a catalog rule.");
            case ProductInvariantViolation violation ->
                conflict(productCode(violation), "The product change violates a catalog rule.");
            case CollectionInvariantViolation violation ->
                conflict(
                        "COLLECTION_" + violation.invariant().name(), "The collection change violates a catalog rule.");
            case DataIntegrityViolationException ignored ->
                conflict("CATALOG_CONFLICT", "The catalog change conflicts with existing data.");
            case IllegalArgumentException ignored -> invalidRequest("The request command is invalid.");
            default ->
                new Descriptor(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "Internal server error",
                        "The request could not be completed.");
        };
    }

    private static String productCode(ProductInvariantViolation violation) {
        return switch (violation.invariant()) {
            case SLUG_REUSE, SLUG_NAMESPACE_CONFLICT -> "PRODUCT_SLUG_CONFLICT";
            case DUPLICATE_SKU -> "SKU_CONFLICT";
            case DUPLICATE_VARIANT_COMBINATION -> "VARIANT_COMBINATION_CONFLICT";
            default -> violation.invariant().name();
        };
    }

    private static Descriptor staleVersion() {
        return new Descriptor(
                HttpStatus.PRECONDITION_FAILED,
                "STALE_RESOURCE_VERSION",
                "Resource version is stale",
                "Reload the resource and retry with its current ETag.");
    }

    private static Descriptor notFound(String code, String detail) {
        return new Descriptor(HttpStatus.NOT_FOUND, code, "Catalog resource not found", detail);
    }

    private static Descriptor conflict(String code, String detail) {
        return new Descriptor(HttpStatus.CONFLICT, code, "Catalog command conflict", detail);
    }

    private static Descriptor invalidRequest(String detail) {
        return new Descriptor(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request", detail);
    }

    private static String normalizeValidationCode(String code) {
        return code.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private static String validationCode(MessageSourceResolvable error) {
        var codes = error.getCodes();
        return codes == null || codes.length == 0 ? "INVALID" : normalizeValidationCode(codes[0]);
    }

    private record Descriptor(HttpStatus status, String code, String title, String detail) {}
}
