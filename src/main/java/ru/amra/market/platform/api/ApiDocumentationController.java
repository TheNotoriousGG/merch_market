package ru.amra.market.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

/** Serves the canonical contract only when API documentation is explicitly enabled. */
@RestController
@RequestMapping("/internal/api-docs")
@ConditionalOnProperty(prefix = "amra.api-docs", name = "enabled", havingValue = "true")
public final class ApiDocumentationController {

    private static final MediaType YAML = MediaType.parseMediaType("application/yaml");
    private static final String ENDPOINT_PREFIX = "/internal/api-docs/";
    private static final Pattern CONTRACT_RESOURCE =
            Pattern.compile("(?:openapi|paths/[a-z0-9-]+|components/[a-z-]+/[a-z0-9-]+)\\.yaml");

    @GetMapping(
            value = {"/openapi.yaml", "/paths/*.yaml", "/components/*/*.yaml"},
            produces = "application/yaml")
    public ResponseEntity<byte[]> getOpenApiContractResource(HttpServletRequest request) throws IOException {
        var requestPath = UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        var resourcePath = requestPath.substring(requestPath.indexOf(ENDPOINT_PREFIX) + ENDPOINT_PREFIX.length());
        if (!CONTRACT_RESOURCE.matcher(resourcePath).matches()) {
            return ResponseEntity.notFound().build();
        }

        var resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        var contract = resource.getContentAsByteArray();
        return ResponseEntity.ok().contentType(YAML).body(contract);
    }
}
