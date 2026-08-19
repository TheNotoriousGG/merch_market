package ru.amra.market.catalog.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import ru.amra.market.catalog.application.port.CatalogAuditContextProvider;
import ru.amra.market.catalog.application.port.CatalogAuditWriter;

/** Builds bounded safe diffs and appends them inside the command transaction. */
@Service
public class CatalogAuditTrail {

    private final CatalogAuditContextProvider contextProvider;
    private final CatalogAuditWriter writer;

    public CatalogAuditTrail(CatalogAuditContextProvider contextProvider, CatalogAuditWriter writer) {
        this.contextProvider = contextProvider;
        this.writer = writer;
    }

    /** Records a successful state change without customer data or storage secrets. */
    public void record(
            String entityType,
            UUID entityId,
            String action,
            @Nullable Long fromVersion,
            long toVersion,
            Map<String, String> details) {
        var safeDiff = new LinkedHashMap<String, String>();
        if (fromVersion != null) {
            safeDiff.put("fromVersion", fromVersion.toString());
        }
        safeDiff.put("toVersion", Long.toString(toVersion));
        safeDiff.putAll(details);
        writer.append(new CatalogAuditEvent(
                contextProvider.current(),
                entityType,
                entityId,
                action,
                "Authorized catalog administration command",
                safeDiff));
    }
}
