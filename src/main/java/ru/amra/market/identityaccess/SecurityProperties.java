package ru.amra.market.identityaccess;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Validated security policy values that may vary between deployment environments. */
@ConfigurationProperties("amra.security")
public record SecurityProperties(
        List<String> allowedOrigins,
        Duration customerIdleTimeout,
        Duration customerAbsoluteTimeout,
        Duration adminIdleTimeout,
        Duration adminAbsoluteTimeout,
        List<String> adminMfaAcrValues) {

    public SecurityProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
        adminMfaAcrValues = List.copyOf(adminMfaAcrValues);
        requirePositive(customerIdleTimeout, "customerIdleTimeout");
        requirePositive(customerAbsoluteTimeout, "customerAbsoluteTimeout");
        requirePositive(adminIdleTimeout, "adminIdleTimeout");
        requirePositive(adminAbsoluteTimeout, "adminAbsoluteTimeout");
        if (allowedOrigins.isEmpty() || adminMfaAcrValues.isEmpty()) {
            throw new IllegalArgumentException("CORS origins and accepted admin MFA ACR values must not be empty");
        }
        allowedOrigins.forEach(SecurityProperties::requireExactHttpOrigin);
        if (adminMfaAcrValues.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Accepted admin MFA ACR values must not be blank");
        }
        if (adminIdleTimeout.compareTo(adminAbsoluteTimeout) >= 0
                || customerIdleTimeout.compareTo(customerAbsoluteTimeout) >= 0) {
            throw new IllegalArgumentException("Idle timeout must be shorter than absolute timeout");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireExactHttpOrigin(String value) {
        final URI origin;
        try {
            origin = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("CORS allowlist entries must be exact HTTP origins", exception);
        }
        var validScheme = "https".equals(origin.getScheme()) || "http".equals(origin.getScheme());
        if (!validScheme
                || origin.getHost() == null
                || origin.getUserInfo() != null
                || (origin.getPath() != null && !origin.getPath().isEmpty())
                || origin.getQuery() != null
                || origin.getFragment() != null) {
            throw new IllegalArgumentException("CORS allowlist entries must be exact HTTP origins");
        }
    }
}
