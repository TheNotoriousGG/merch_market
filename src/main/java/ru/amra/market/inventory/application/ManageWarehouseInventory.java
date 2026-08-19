package ru.amra.market.inventory.application;

import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.integration.CatalogVariantInventoryView;
import ru.amra.market.inventory.application.port.InventoryActorScopeProvider;
import ru.amra.market.inventory.application.port.InventoryAdminBalanceReader;
import ru.amra.market.inventory.application.port.InventoryIdempotencyStore;
import ru.amra.market.inventory.application.port.InventoryStockCommandResultRepository;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.MovementId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.MovementReference;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** Protected warehouse use cases with exact idempotent command replay. */
@Service
public class ManageWarehouseInventory {
    private static final String WAREHOUSE_CODE = "PRIMARY";
    private static final String RECEIVE_OPERATION = "RECEIVE_STOCK";
    private static final String RECONCILE_OPERATION = "RECONCILE_STOCK";

    private final ManageInventoryStock stock;
    private final InventoryAdminBalanceReader balances;
    private final InventoryIdempotencyStore idempotency;
    private final InventoryStockCommandResultRepository results;
    private final InventoryActorScopeProvider actors;
    private final CatalogVariantInventoryView catalog;
    private final InventoryAuditTrail audit;

    public ManageWarehouseInventory(
            ManageInventoryStock stock,
            InventoryAdminBalanceReader balances,
            InventoryIdempotencyStore idempotency,
            InventoryStockCommandResultRepository results,
            InventoryActorScopeProvider actors,
            CatalogVariantInventoryView catalog,
            InventoryAuditTrail audit) {
        this.stock = stock;
        this.balances = balances;
        this.idempotency = idempotency;
        this.results = results;
        this.actors = actors;
        this.catalog = catalog;
        this.audit = audit;
    }

    /** Reads the exact primary-warehouse balance. */
    @Transactional(readOnly = true)
    public InventoryAdminBalanceView get(UUID variantId) {
        return balances.findPrimary(variantId).orElseThrow(InventoryBalanceNotFoundException::new);
    }

    /** Receives physical units or exactly replays the original caller-scoped result. */
    @Transactional
    public WarehouseMutationResult receive(
            UUID variantId, long quantity, String reason, @Nullable String reference, String idempotencyKey) {
        var actor = actors.currentActorScope();
        var fingerprint = InventoryCommandFingerprint.of(variantId, quantity, reason, reference);
        var replay = idempotency.claim(actor, idempotencyKey, RECEIVE_OPERATION, fingerprint);
        if (replay.isPresent()) {
            return replay(replay.orElseThrow());
        }
        if (!catalog.findActiveVariantIds(Set.of(variantId)).contains(variantId)) {
            throw new InventoryVariantNotActiveException();
        }
        var mutation = stock.receive(new ReceiveStockCommand(
                new WarehouseId(balances.primaryWarehouseId()),
                new CatalogVariantId(variantId),
                new StockQuantity(quantity),
                new MovementReason(reason),
                reference == null ? null : new MovementReference(reference)));
        audit.record("STOCK_RECEIVED", mutation);
        results.insert(mutation, mutation.movement().occurredAt());
        idempotency.complete(actor, idempotencyKey, mutation.movement().id().value());
        return new WarehouseMutationResult(
                WAREHOUSE_CODE, mutation, mutation.movement().occurredAt());
    }

    /** Reconciles exact on-hand or exactly replays the original caller-scoped result. */
    @Transactional
    public WarehouseMutationResult reconcile(
            UUID variantId,
            long onHand,
            long expectedVersion,
            String reason,
            @Nullable String reference,
            String idempotencyKey) {
        var actor = actors.currentActorScope();
        var fingerprint = InventoryCommandFingerprint.of(variantId, onHand, expectedVersion, reason, reference);
        var replay = idempotency.claim(actor, idempotencyKey, RECONCILE_OPERATION, fingerprint);
        if (replay.isPresent()) {
            return replay(replay.orElseThrow());
        }
        var mutation = stock.reconcile(new ReconcileStockCommand(
                new WarehouseId(balances.primaryWarehouseId()),
                new CatalogVariantId(variantId),
                new StockQuantity(onHand),
                expectedVersion,
                new MovementReason(reason),
                reference == null ? null : new MovementReference(reference)));
        audit.record("STOCK_RECONCILED", mutation);
        results.insert(mutation, mutation.movement().occurredAt());
        idempotency.complete(actor, idempotencyKey, mutation.movement().id().value());
        return new WarehouseMutationResult(
                WAREHOUSE_CODE, mutation, mutation.movement().occurredAt());
    }

    private WarehouseMutationResult replay(UUID movementId) {
        var result = results.find(new MovementId(movementId)).orElseThrow();
        return new WarehouseMutationResult(WAREHOUSE_CODE, result.mutation(), result.balanceUpdatedAt());
    }
}
