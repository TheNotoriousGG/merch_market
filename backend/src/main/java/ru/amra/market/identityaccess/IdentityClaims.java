package ru.amra.market.identityaccess;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

final class IdentityClaims {

    private IdentityClaims() {}

    static Set<GrantedAuthority> authorities(OidcUser user) {
        var authorities = new LinkedHashSet<GrantedAuthority>(user.getAuthorities());
        realmRoles(user.getClaims()).stream()
                .map(AccessRole::fromRealmRole)
                .flatMap(java.util.Optional::stream)
                .map(AccessRole::authority)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return Set.copyOf(authorities);
    }

    static Set<AccessRole> accessRoles(OidcUser user) {
        var roles = new LinkedHashSet<AccessRole>();
        realmRoles(user.getClaims()).stream()
                .map(AccessRole::fromRealmRole)
                .flatMap(java.util.Optional::stream)
                .forEach(roles::add);
        return Set.copyOf(roles);
    }

    static boolean emailVerified(OidcUser user) {
        return Boolean.TRUE.equals(user.getClaim("email_verified"));
    }

    private static Collection<String> realmRoles(Map<String, Object> claims) {
        var realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
            return Set.of();
        }
        var roles = realmAccessMap.get("roles");
        if (!(roles instanceof Collection<?> roleCollection)) {
            return Set.of();
        }
        return roleCollection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
