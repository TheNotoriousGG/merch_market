package ru.amra.market.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void everyExternalReferenceResolvesInsideTheContractTree() throws IOException {
        var referencePattern = Pattern.compile("\\$ref: ([^#\\s]+)");

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
