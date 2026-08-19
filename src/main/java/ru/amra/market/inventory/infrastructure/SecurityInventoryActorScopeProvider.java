package ru.amra.market.inventory.infrastructure;

import static java.util.Objects.requireNonNull;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.amra.market.inventory.application.port.InventoryActorScopeProvider;

/** Reads the stable authenticated subject without exposing security types to application code. */
@Component
class SecurityInventoryActorScopeProvider implements InventoryActorScopeProvider {
    @Override
    public String currentActorScope() {
        return requireNonNull(SecurityContextHolder.getContext().getAuthentication())
                .getName();
    }
}
