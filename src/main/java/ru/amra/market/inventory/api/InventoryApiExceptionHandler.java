package ru.amra.market.inventory.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.amra.market.inventory.application.InventoryBalanceNotFoundException;
import ru.amra.market.inventory.application.InventoryIdempotencyConflictException;
import ru.amra.market.inventory.application.InventoryVariantNotActiveException;
import ru.amra.market.inventory.application.StaleInventoryVersionException;
import ru.amra.market.inventory.domain.InventoryInvariantViolation;
import ru.amra.market.platform.generated.model.ProblemDetailsDto;
import ru.amra.market.platform.web.ApiProblemFactory;

/** Maps inventory-owned failures without creating a reverse dependency from catalog. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public final class InventoryApiExceptionHandler {
    private final ApiProblemFactory problems;

    public InventoryApiExceptionHandler(ApiProblemFactory problems) {
        this.problems = problems;
    }

    /** Converts inventory domain and application failures to stable RFC 9457 codes. */
    @ExceptionHandler({
        InventoryBalanceNotFoundException.class,
        InventoryIdempotencyConflictException.class,
        InventoryVariantNotActiveException.class,
        StaleInventoryVersionException.class,
        InventoryInvariantViolation.class
    })
    ResponseEntity<ProblemDetailsDto> inventoryFailure(RuntimeException exception, HttpServletRequest request) {
        var descriptor = describe(exception);
        var body = problems.create(
                descriptor.status(), descriptor.code(), descriptor.title(), descriptor.detail(), request, List.of());
        return ResponseEntity.status(descriptor.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static Descriptor describe(RuntimeException exception) {
        return switch (exception) {
            case InventoryBalanceNotFoundException ignored ->
                new Descriptor(
                        HttpStatus.NOT_FOUND,
                        "INVENTORY_BALANCE_NOT_FOUND",
                        "Inventory balance not found",
                        "Inventory balance was not found.");
            case InventoryVariantNotActiveException ignored ->
                conflict("INVENTORY_VARIANT_NOT_ACTIVE", "The catalog variant is not active.");
            case InventoryIdempotencyConflictException ignored ->
                conflict("INVENTORY_IDEMPOTENCY_CONFLICT", "The idempotency key belongs to a different command.");
            case StaleInventoryVersionException ignored ->
                new Descriptor(
                        HttpStatus.PRECONDITION_FAILED,
                        "STALE_RESOURCE_VERSION",
                        "Resource version is stale",
                        "Reload the resource and retry with its current ETag.");
            case InventoryInvariantViolation violation ->
                conflict(inventoryCode(violation), "The inventory command violates a stock rule.");
            default -> throw new IllegalArgumentException("Unsupported inventory exception", exception);
        };
    }

    private static String inventoryCode(InventoryInvariantViolation violation) {
        return switch (violation.invariant()) {
            case NO_PHYSICAL_CHANGE -> "INVENTORY_NO_CHANGE";
            default -> violation.invariant().name();
        };
    }

    private static Descriptor conflict(String code, String detail) {
        return new Descriptor(HttpStatus.CONFLICT, code, "Inventory command conflict", detail);
    }

    private record Descriptor(HttpStatus status, String code, String title, String detail) {}
}
