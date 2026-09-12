package ru.amra.market.inventory.application;

import java.util.LinkedHashMap;
import org.springframework.stereotype.Service;
import ru.amra.market.inventory.application.port.InventoryAuditContextProvider;
import ru.amra.market.inventory.application.port.InventoryAuditWriter;
import ru.amra.market.inventory.domain.InventoryMutation;

/** Builds bounded quantity/version diffs and appends them in the stock command transaction. */
@Service
public class InventoryAuditTrail {
    private final InventoryAuditContextProvider context;
    private final InventoryAuditWriter writer;

    public InventoryAuditTrail(InventoryAuditContextProvider context, InventoryAuditWriter writer) {
        this.context = context;
        this.writer = writer;
    }

    /** Records one successful physical warehouse mutation. */
    public void record(String action, InventoryMutation mutation) {
        var after = mutation.balance();
        var beforeOnHand = after.onHand().value() - mutation.movement().quantityDelta();
        var safeDiff = new LinkedHashMap<String, String>();
        safeDiff.put("fromOnHand", Long.toString(beforeOnHand));
        safeDiff.put("fromReserved", Long.toString(after.reserved().value()));
        safeDiff.put(
                "fromAvailable", Long.toString(beforeOnHand - after.reserved().value()));
        safeDiff.put("fromVersion", Long.toString(after.version() - 1));
        safeDiff.put("toOnHand", Long.toString(after.onHand().value()));
        safeDiff.put("toReserved", Long.toString(after.reserved().value()));
        safeDiff.put("toAvailable", Long.toString(after.available().value()));
        safeDiff.put("toVersion", Long.toString(after.version()));
        writer.append(new InventoryAuditEvent(
                context.current(),
                mutation.movement().occurredAt(),
                after.warehouseId(),
                after.variantId(),
                action,
                mutation.movement().reason(),
                mutation.movement().reference(),
                safeDiff));
    }
}
