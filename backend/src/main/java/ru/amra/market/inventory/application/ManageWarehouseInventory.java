package ru.amra.market.inventory.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /** Builds the warehouse workspace without crossing persistence ownership boundaries. */
    @Transactional(readOnly = true)
    public InventoryWarehouseOverview overview() {
        var variants = catalog.listWarehouseVariants();
        var byId = variants.stream()
                .collect(
                        Collectors.toMap(CatalogVariantInventoryView.WarehouseVariant::variantId, Function.identity()));
        var existing = balances.findPrimary(byId.keySet());
        var items = variants.stream()
                .map(variant -> {
                    var balance = existing.get(variant.variantId());
                    return new InventoryWarehouseOverview.StockItem(
                            variant.productId(),
                            variant.productName(),
                            variant.productStatus(),
                            variant.variantId(),
                            variant.sku(),
                            variant.variantLabel(),
                            balance == null ? 0 : balance.onHand(),
                            balance == null ? 0 : balance.reserved(),
                            balance == null ? 0 : balance.available(),
                            balance == null ? 0 : balance.version(),
                            balance == null ? null : balance.updatedAt());
                })
                .toList();
        var movements = balances.latestMovements(100).stream()
                .map(movement -> {
                    var variant = byId.get(movement.variantId());
                    return new InventoryWarehouseOverview.Movement(
                            movement.id(),
                            movement.variantId(),
                            variant == null ? "Архивный товар" : variant.productName(),
                            variant == null ? "—" : variant.sku(),
                            variant == null ? "—" : variant.variantLabel(),
                            movement.type(),
                            movement.quantityDelta(),
                            movement.reason(),
                            movement.reference(),
                            movement.occurredAt());
                })
                .toList();
        return new InventoryWarehouseOverview(items, movements);
    }

    /** Returns an opaque-cursor page of immutable warehouse movements. */
    @Transactional(readOnly = true)
    public MovementPage movements(@Nullable String cursor, int requestedSize) {
        var size = Math.max(1, Math.min(requestedSize, 100));
        var position = decodeCursor(cursor);
        var rows = balances.movementPage(
                size + 1, position == null ? null : position.occurredAt(), position == null ? null : position.id());
        var hasNext = rows.size() > size;
        var pageRows = hasNext ? rows.subList(0, size) : rows;
        var variants = catalog.listWarehouseVariants().stream()
                .collect(
                        Collectors.toMap(CatalogVariantInventoryView.WarehouseVariant::variantId, Function.identity()));
        var items = pageRows.stream()
                .map(movement -> {
                    var variant = variants.get(movement.variantId());
                    return new InventoryWarehouseOverview.Movement(
                            movement.id(),
                            movement.variantId(),
                            variant == null ? "Архивный товар" : variant.productName(),
                            variant == null ? "—" : variant.sku(),
                            variant == null ? "—" : variant.variantLabel(),
                            movement.type(),
                            movement.quantityDelta(),
                            movement.reason(),
                            movement.reference(),
                            movement.occurredAt());
                })
                .toList();
        var next = hasNext ? encodeCursor(pageRows.getLast()) : null;
        return new MovementPage(items, next, hasNext);
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
        if (!catalog.findReceivableVariantIds(Set.of(variantId)).contains(variantId)) {
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

    /** Receives every document line atomically and replays it by one caller-scoped key. */
    @Transactional
    public ReceiptDocument receiveDocument(
            List<ReceiptLine> lines, String reason, @Nullable String reference, String idempotencyKey) {
        if (lines.isEmpty() || lines.size() > 200) {
            throw new IllegalArgumentException("Receipt must contain between 1 and 200 lines");
        }
        if (lines.stream().map(ReceiptLine::variantId).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("Receipt variants must be unique");
        }
        var mutations = java.util.stream.IntStream.range(0, lines.size())
                .mapToObj(index -> {
                    var line = lines.get(index);
                    return receive(line.variantId(), line.quantity(), reason, reference, idempotencyKey + ":" + index);
                })
                .toList();
        return new ReceiptDocument(
                idempotencyKey,
                mutations,
                lines.stream().mapToLong(ReceiptLine::quantity).sum());
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

    public record ReceiptLine(UUID variantId, long quantity) {}

    public record ReceiptDocument(String documentId, List<WarehouseMutationResult> movements, long totalQuantity) {}

    public record MovementPage(
            List<InventoryWarehouseOverview.Movement> items,
            @Nullable String nextCursor,
            boolean hasNext) {}

    private static @Nullable CursorPosition decodeCursor(@Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            var value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
            return new CursorPosition(Instant.parse(value[0]), UUID.fromString(value[1]));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid movement cursor", exception);
        }
    }

    private static String encodeCursor(InventoryAdminBalanceReader.MovementView movement) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((movement.occurredAt() + "|" + movement.id()).getBytes(StandardCharsets.UTF_8));
    }

    private record CursorPosition(Instant occurredAt, UUID id) {}
}
