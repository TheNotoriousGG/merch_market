package ru.amra.market.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class IdentityClaimsTests {

    @Test
    void mapsOnlyApplicationRolesToAuthorities() {
        var user = mock(OidcUser.class);
        doReturn(Set.of(new SimpleGrantedAuthority("OIDC_USER"))).when(user).getAuthorities();
        when(user.getClaims())
                .thenReturn(Map.of("realm_access", Map.of("roles", List.of("CUSTOMER", "ADMIN", "realm-management"))));

        assertThat(IdentityClaims.authorities(user))
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("OIDC_USER", "ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void returnsNoApplicationRolesForMalformedRealmClaims() {
        var user = mock(OidcUser.class);
        when(user.getClaims()).thenReturn(Map.of("realm_access", "not-an-object"));
        assertThat(IdentityClaims.accessRoles(user)).isEmpty();

        when(user.getClaims()).thenReturn(Map.of("realm_access", Map.of("roles", "not-a-list")));
        assertThat(IdentityClaims.accessRoles(user)).isEmpty();
    }
}
