package ru.amra.market.inventory.infrastructure.audit;

import static java.util.Objects.requireNonNull;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.amra.market.inventory.application.InventoryAuditContext;
import ru.amra.market.inventory.application.port.InventoryAuditContextProvider;
import ru.amra.market.platform.web.TraceContext;

/** Adapts the authenticated warehouse session and current request trace to the audit port. */
@Component
class SecurityInventoryAuditContextProvider implements InventoryAuditContextProvider {

    @Override
    public InventoryAuditContext current() {
        var authentication = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
        return new InventoryAuditContext(authentication.getName(), TraceContext.currentId());
    }
}
