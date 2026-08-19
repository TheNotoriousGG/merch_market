package ru.amra.market.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OpenApiPolicyTests {

    private static final Path CONTRACT = Path.of("src/main/openapi/openapi.yaml");

    @Test
    void contractDefinesTheApprovedApiAndTransportConventions() throws IOException {
        var contract = Files.readString(CONTRACT);

        assertThat(contract)
                .contains(
                        "openapi: 3.0.3",
                        "- url: /api/v1",
                        "ProblemDetails:",
                        "ValidationViolation:",
                        "PageMetadata:",
                        "CursorMetadata:",
                        "IdempotencyKey:",
                        "IfMatch:",
                        "ETag:",
                        "RetryAfter:",
                        "TraceId:");
    }

    @Test
    void catalogContractKeepsPublicAndAdministrativeBoundariesExplicit() throws IOException {
        var contract = Files.readString(CONTRACT);
        var publicProduct =
                Files.readString(CONTRACT.getParent().resolve("components/schemas/catalog-product-detail.yaml"));
        var publicMedia = Files.readString(CONTRACT.getParent().resolve("components/schemas/catalog-media.yaml"));

        assertThat(contract)
                .contains(
                        "/catalog/categories:",
                        "/catalog/products:",
                        "/admin/catalog/categories:",
                        "/admin/catalog/products:",
                        "SessionCookie:");
        assertThat(publicProduct.toLowerCase(Locale.ROOT)).doesNotContain("price", "stock", "availability");
        assertThat(publicMedia).doesNotContain("objectKey");
    }

    @Test
    void catalogListUsesTheApprovedPaginationFiltersAndSortAllowlist() throws IOException {
        var pageSize = Files.readString(CONTRACT.getParent().resolve("components/parameters/catalog-page-size.yaml"));
        var sort = Files.readString(CONTRACT.getParent().resolve("components/parameters/catalog-sort.yaml"));
        var listPath = Files.readString(CONTRACT.getParent().resolve("paths/catalog-products.yaml"));

        assertThat(pageSize).contains("default: 24", "maximum: 60");
        assertThat(sort).contains("MANUAL", "NEWEST", "NAME_ASC").doesNotContain("PRICE_ASC");
        assertThat(listPath)
                .contains(
                        "category-slug.yaml",
                        "collection-slug.yaml",
                        "catalog-query.yaml",
                        "new-only.yaml",
                        "size-values.yaml",
                        "color-values.yaml");
    }

    @Test
    void inventoryContractSeparatesPublicAvailabilityFromWarehouseQuantities() throws IOException {
        var contractRoot = CONTRACT.getParent();
        var contract = Files.readString(CONTRACT);
        var availability = Files.readString(contractRoot.resolve("components/schemas/inventory-availability.yaml"));
        var balance = Files.readString(contractRoot.resolve("components/schemas/inventory-balance.yaml"));
        var publicPath = Files.readString(contractRoot.resolve("paths/inventory-availability.yaml"));
        var receiptPath = Files.readString(contractRoot.resolve("paths/admin-inventory-receipts.yaml"));
        var adjustmentPath = Files.readString(contractRoot.resolve("paths/admin-inventory-adjustments.yaml"));

        assertThat(contract)
                .contains(
                        "/inventory/availability:",
                        "/admin/inventory/balances/{variantId}:",
                        "/admin/inventory/balances/{variantId}/receipts:",
                        "/admin/inventory/balances/{variantId}/adjustments:")
                .doesNotContain("/inventory/reservations:");
        assertThat(availability).contains("IN_STOCK", "OUT_OF_STOCK").doesNotContain("onHand", "reserved");
        assertThat(balance).contains("onHand", "reserved", "available", "version");
        assertThat(publicPath).doesNotContain("security: [{SessionCookie: []}]");
        assertThat(receiptPath)
                .contains("security: [{SessionCookie: []}]", "idempotency-key.yaml", "csrf-token.yaml", "X-Trace-Id:");
        assertThat(adjustmentPath)
                .contains("if-match.yaml", "idempotency-key.yaml", "csrf-token.yaml", "\"412\"", "\"428\"");
    }

    @Test
    void administrativeMutationsDeclareSessionCsrfAndConcurrencyRequirements() throws IOException {
        var contractRoot = CONTRACT.getParent();
        var mutableExistingResources = java.util.List.of(
                "admin-catalog-category.yaml",
                "admin-catalog-product.yaml",
                "admin-catalog-product-transition.yaml",
                "admin-catalog-product-variants.yaml",
                "admin-catalog-product-variant.yaml",
                "admin-catalog-product-media.yaml",
                "admin-catalog-product-media-item.yaml",
                "admin-catalog-collection.yaml",
                "admin-catalog-collection-products.yaml");

        for (var filename : mutableExistingResources) {
            var path = Files.readString(contractRoot.resolve("paths").resolve(filename));
            assertThat(path)
                    .as(filename)
                    .contains(
                            "security: [{SessionCookie: []}]",
                            "if-match.yaml",
                            "csrf-token.yaml",
                            "\"412\"",
                            "\"428\"");
        }
    }

    @Test
    void everyCatalogResponseDocumentsItsTraceIdentifier() throws IOException {
        var contractRoot = CONTRACT.getParent();
        var successfulResponsePattern =
                Pattern.compile("(?ms)^    \"(?:2|3)\\d{2}\":.*?(?=^    \"\\d{3}\":|^[a-z]+:|\\z)");

        try (var paths = Files.list(contractRoot.resolve("paths"))) {
            for (var path : paths.toList()) {
                var pathName = path.getFileName().toString();
                if (!pathName.contains("catalog") && !pathName.contains("inventory")) {
                    continue;
                }
                var contract = Files.readString(path);
                var responses =
                        successfulResponsePattern.matcher(contract).results().toList();
                assertThat(responses)
                        .as("success responses in %s", path.getFileName())
                        .isNotEmpty();
                for (var response : responses) {
                    assertThat(response.group())
                            .as("success response trace header in %s", path.getFileName())
                            .contains("X-Trace-Id:", "../components/headers/trace-id.yaml");
                }
            }
        }

        try (var responses = Files.list(contractRoot.resolve("components/responses"))) {
            for (var response : responses.toList()) {
                assertThat(Files.readString(response))
                        .as("error response trace header in %s", response.getFileName())
                        .contains("X-Trace-Id:", "../headers/trace-id.yaml");
            }
        }
    }

    @Test
    void everyExternalReferenceResolvesInsideTheContractTree() throws IOException {
        var referencePattern = Pattern.compile("\\$ref: ([^#\\s}\\],]+)");

        try (var files = Files.walk(CONTRACT.getParent())) {
            var yamlFiles =
                    files.filter(path -> path.toString().endsWith(".yaml")).toList();
            for (var source : yamlFiles) {
                var matcher = referencePattern.matcher(Files.readString(source));
                while (matcher.find()) {
                    assertThat(source.getParent().resolve(matcher.group(1)).normalize())
                            .as("reference %s declared in %s", matcher.group(1), source)
                            .isRegularFile();
                }
            }
        }
    }
}
