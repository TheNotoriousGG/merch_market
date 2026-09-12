package ru.amra.market.identityaccess;

import java.time.Duration;
import org.springframework.security.core.Authentication;

/** Selects idle and absolute session lifetimes from the authenticated privilege level. */
public final class SessionLifetimePolicy {

    private final SecurityProperties properties;

    public SessionLifetimePolicy(SecurityProperties properties) {
        this.properties = properties;
    }

    public Duration idleTimeout(Authentication authentication) {
        return isAdministrator(authentication) ? properties.adminIdleTimeout() : properties.customerIdleTimeout();
    }

    public Duration absoluteTimeout(Authentication authentication) {
        return isAdministrator(authentication)
                ? properties.adminAbsoluteTimeout()
                : properties.customerAbsoluteTimeout();
    }

    private static boolean isAdministrator(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> AccessRole.ADMIN.authority().equals(authority.getAuthority()));
    }
}
