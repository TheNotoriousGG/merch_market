package ru.amra.market.inventory.application;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.integration.CatalogVariantInventoryView;
import ru.amra.market.inventory.application.port.InventoryAvailabilityReader;

/** Public privacy-preserving batch availability query. */
@Service
public class GetInventoryAvailability {
    private final CatalogVariantInventoryView catalog;
    private final InventoryAvailabilityReader availability;
    private final Clock clock;

    public GetInventoryAvailability(
            CatalogVariantInventoryView catalog, InventoryAvailabilityReader availability, Clock clock) {
        this.catalog = catalog;
        this.availability = availability;
        this.clock = clock;
    }

    /** De-duplicates first-seen identifiers and hides unknown/missing balances as out of stock. */
    @Transactional(readOnly = true)
    public InventoryAvailabilitySnapshot execute(List<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty() || variantIds.size() > 60) {
            throw new IllegalArgumentException("Availability request must contain between 1 and 60 identifiers");
        }
        var ordered = new LinkedHashSet<>(variantIds);
        var active = catalog.findActiveVariantIds(Set.copyOf(ordered));
        var inStock = availability.findInStock(active);
        var items = ordered.stream()
                .map(id -> new InventoryAvailability(
                        id, inStock.contains(id) ? AvailabilityStatus.IN_STOCK : AvailabilityStatus.OUT_OF_STOCK))
                .toList();
        return new InventoryAvailabilitySnapshot(items, clock.instant());
    }
}
