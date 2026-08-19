package ru.amra.market.identityaccess;

import java.util.Locale;
import java.util.Optional;

/** Permissions represented as Keycloak realm roles and Spring Security authorities. */
public enum AccessRole {
    CUSTOMER,
    CATALOG_MANAGER,
    ORDER_MANAGER,
    WAREHOUSE_MANAGER,
    SUPPORT,
    ADMIN;

    static Optional<AccessRole> fromRealmRole(String role) {
        try {
            return Optional.of(valueOf(role.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    String authority() {
        return "ROLE_" + name();
    }
}
