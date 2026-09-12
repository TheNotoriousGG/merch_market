package ru.amra.market.catalog.infrastructure.audit;

import static java.util.Objects.requireNonNull;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import ru.amra.market.catalog.application.CatalogAuditContext;
import ru.amra.market.catalog.application.port.CatalogAuditContextProvider;
import ru.amra.market.platform.web.TraceContext;

/** Adapts the authenticated backend session and current request trace to the audit port. */
@Component
class SecurityCatalogAuditContextProvider implements CatalogAuditContextProvider {

    @Override
    public CatalogAuditContext current() {
        var authentication = requireNonNull(SecurityContextHolder.getContext().getAuthentication());
        return new CatalogAuditContext(authentication.getName(), TraceContext.currentId());
    }
}
