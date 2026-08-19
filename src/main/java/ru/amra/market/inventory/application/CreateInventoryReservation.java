package ru.amra.market.inventory.application;

import static java.util.Objects.requireNonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.integration.CatalogVariantInventoryView;
import ru.amra.market.inventory.application.contract.CreateInventoryReservationRequest;
import ru.amra.market.inventory.application.contract.InventoryReservationOperations;
import ru.amra.market.inventory.application.port.InventoryIdGenerator;
import ru.amra.market.inventory.application.port.InventoryIdempotencyStore;
import ru.amra.market.inventory.application.port.InventoryReservationRepository;
import ru.amra.market.inventory.application.port.InventoryStockRepository;
import ru.amra.market.inventory.application.port.InventoryStockRepository.InventoryBalanceKey;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryReservation;
import ru.amra.market.inventory.domain.ReservationEventId;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.ReservationLine;
import ru.amra.market.inventory.domain.ReservationLines;
import ru.amra.market.inventory.domain.ReservationOwnerReference;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** Transactional all-or-nothing reservation creator. */
@Service
public class CreateInventoryReservation implements InventoryReservationOperations {
    private static final String OPERATION = "CREATE_RESERVATION";

    private final InventoryStockRepository stock;
    private final InventoryReservationRepository reservations;
    private final InventoryIdempotencyStore idempotency;
    private final InventoryIdGenerator ids;
    private final CatalogVariantInventoryView catalog;
    private final Clock clock;

    public CreateInventoryReservation(
            InventoryStockRepository stock,
            InventoryReservationRepository reservations,
            InventoryIdempotencyStore idempotency,
            InventoryIdGenerator ids,
            CatalogVariantInventoryView catalog,
            Clock clock) {
        this.stock = stock;
        this.reservations = reservations;
        this.idempotency = idempotency;
        this.ids = ids;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    @Transactional
    public InventoryReservation create(CreateInventoryReservationRequest request) {
        var owner = new ReservationOwnerReference(request.ownerReference());
        var lines = ReservationLines.of(request.lines().stream()
                .map(line -> new ReservationLine(
                        new WarehouseId(line.warehouseId()),
                        new CatalogVariantId(line.variantId()),
                        new StockQuantity(line.quantity())))
                .toList());
        var actorScope = owner.value().toString();
        var replay = idempotency.claim(actorScope, request.idempotencyKey(), OPERATION, fingerprint(owner, lines));
        if (replay.isPresent()) {
            return reservations.find(new ReservationId(replay.orElseThrow())).orElseThrow();
        }
        verifyActiveVariants(lines);
        var locked = stock.lockOrCreateAll(lines.values().stream()
                .map(line -> new InventoryBalanceKey(line.warehouseId(), line.variantId()))
                .toList());
        var byKey = locked.stream()
                .collect(Collectors.toMap(
                        balance -> new InventoryBalanceKey(balance.warehouseId(), balance.variantId()),
                        balance -> balance));
        var now = clock.instant();
        lines.values().stream()
                .map(line -> requireNonNull(byKey.get(new InventoryBalanceKey(line.warehouseId(), line.variantId())))
                        .reserve(line.quantity()))
                .sorted((left, right) -> key(left.warehouseId(), left.variantId())
                        .compareTo(key(right.warehouseId(), right.variantId())))
                .forEach(balance -> stock.saveBalance(balance, now));
        var reservationId = new ReservationId(ids.next());
        var creation = InventoryReservation.create(
                reservationId, new ReservationEventId(ids.next()), owner, lines, now, InventoryReservation.DEFAULT_TTL);
        reservations.insert(creation);
        idempotency.complete(actorScope, request.idempotencyKey(), reservationId.value());
        return creation.reservation();
    }

    private void verifyActiveVariants(ReservationLines lines) {
        var requested =
                lines.values().stream().map(line -> line.variantId().value()).collect(Collectors.toUnmodifiableSet());
        if (!catalog.findActiveVariantIds(requested).equals(requested)) {
            throw new InventoryVariantNotActiveException();
        }
    }

    private static String fingerprint(ReservationOwnerReference owner, ReservationLines lines) {
        var canonicalLines = lines.values().stream()
                .sorted((left, right) -> key(left.warehouseId(), left.variantId())
                        .compareTo(key(right.warehouseId(), right.variantId())))
                .map(line -> line.warehouseId().value() + ":" + line.variantId().value() + ":"
                        + line.quantity().value())
                .toList();
        var canonical = owner.value() + "|" + String.join("|", canonicalLines);
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static InventoryBalanceKey key(WarehouseId warehouseId, CatalogVariantId variantId) {
        return new InventoryBalanceKey(warehouseId, variantId);
    }
}
