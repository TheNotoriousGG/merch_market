package ru.amra.market.identityaccess;

import java.util.Comparator;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.RestController;
import ru.amra.market.platform.generated.api.IdentityApi;
import ru.amra.market.platform.generated.model.CurrentSessionDto;

/** Contract adapter exposing only non-sensitive identity metadata to the browser. */
@RestController
public final class IdentityApiController implements IdentityApi {

    @Override
    public ResponseEntity<CurrentSessionDto> getCurrentSession() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return ResponseEntity.ok(new CurrentSessionDto(false, false, List.of()));
        }

        var permissions = IdentityClaims.accessRoles(user).stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(role -> CurrentSessionDto.PermissionsEnum.fromValue(role.name()))
                .toList();
        var response = new CurrentSessionDto(true, IdentityClaims.emailVerified(user), permissions)
                .subject(user.getSubject())
                .displayName(user.getFullName());
        return ResponseEntity.ok(response);
    }
}
