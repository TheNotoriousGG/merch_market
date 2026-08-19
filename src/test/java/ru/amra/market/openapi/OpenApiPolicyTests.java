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
