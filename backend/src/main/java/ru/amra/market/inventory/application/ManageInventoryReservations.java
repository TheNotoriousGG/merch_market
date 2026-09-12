package ru.amra.market.inventory.application;

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.catalog.application.integration.CatalogVariantInventoryView;
import ru.amra.market.inventory.application.contract.CreateInventoryReservationRequest;
import ru.amra.market.inventory.application.contract.InventoryReservationOperations;
import ru.amra.market.inventory.application.contract.ReservationCommandRequest;
import ru.amra.market.inventory.application.port.InventoryIdGenerator;
import ru.amra.market.inventory.application.port.InventoryIdempotencyStore;
import ru.amra.market.inventory.application.port.InventoryReservationRepository;
import ru.amra.market.inventory.application.port.InventoryStockRepository;
import ru.amra.market.inventory.application.port.InventoryStockRepository.InventoryBalanceKey;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryBalance;
import ru.amra.market.inventory.domain.InventoryReservation;
import ru.amra.market.inventory.domain.MovementId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.MovementReference;
import ru.amra.market.inventory.domain.ReservationEventId;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.ReservationLine;
import ru.amra.market.inventory.domain.ReservationLines;
import ru.amra.market.inventory.domain.ReservationOwnerReference;
import ru.amra.market.inventory.domain.ReservationTransition;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** Transactional all-or-nothing reservation lifecycle application service. */
@Service
public class ManageInventoryReservations implements InventoryReservationOperations {
    private static final String CREATE_OPERATION = "CREATE_RESERVATION";
    private static final String EXTEND_OPERATION = "EXTEND_RESERVATION";
    private static final String COMMIT_OPERATION = "COMMIT_RESERVATION";
    private static final String RELEASE_OPERATION = "RELEASE_RESERVATION";
    private static final MovementReason COMMIT_REASON = new MovementReason("Reservation committed");

    private final InventoryStockRepository stock;
    private final InventoryReservationRepository reservations;
    private final InventoryIdempotencyStore idempotency;
    private final InventoryIdGenerator ids;
    private final CatalogVariantInventoryView catalog;
    private final Clock clock;

    public ManageInventoryReservations(
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
        var lines = toLines(request);
        var scope = scope(owner);
        var replay =
                idempotency.claim(scope, request.idempotencyKey(), CREATE_OPERATION, createFingerprint(owner, lines));
        if (replay.isPresent()) {
            return replay(new ReservationEventId(replay.orElseThrow()), owner);
        }
        verifyActiveVariants(lines);
        var locked = stock.lockOrCreateAll(keys(lines));
        var byKey = byKey(locked);
        var now = clock.instant();
        lines.values().stream()
                .map(line -> balance(byKey, line).reserve(line.quantity()))
                .sorted((left, right) -> key(left.warehouseId(), left.variantId())
                        .compareTo(key(right.warehouseId(), right.variantId())))
                .forEach(balance -> stock.saveBalance(balance, now));
        var creation = InventoryReservation.create(
                new ReservationId(ids.next()),
                new ReservationEventId(ids.next()),
                owner,
                lines,
                now,
                InventoryReservation.DEFAULT_TTL);
        reservations.insert(creation);
        complete(scope, request.idempotencyKey(), creation);
        return creation.reservation();
    }

    @Override
    @Transactional
    public UUID createId(CreateInventoryReservationRequest request) {
        return create(request).id().value();
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryReservation get(UUID ownerReference, UUID reservationId) {
        var owner = new ReservationOwnerReference(ownerReference);
        return requireOwned(reservations.find(new ReservationId(reservationId)), owner);
    }

    @Override
    @Transactional
    public InventoryReservation extend(ReservationCommandRequest request) {
        return transition(
                request,
                EXTEND_OPERATION,
                (reservation, now) ->
                        reservation.extend(new ReservationEventId(ids.next()), now, InventoryReservation.DEFAULT_TTL));
    }

    @Override
    @Transactional
    public InventoryReservation commit(ReservationCommandRequest request) {
        return transition(request, COMMIT_OPERATION, (reservation, now) -> {
            var transition = reservation.commit(new ReservationEventId(ids.next()), now);
            var byKey = byKey(stock.lockAll(keys(reservation.lines())));
            for (var line : reservation.lines().values()) {
                var mutation = balance(byKey, line)
                        .commit(
                                new MovementId(ids.next()),
                                line.quantity(),
                                COMMIT_REASON,
                                new MovementReference(
                                        reservation.ownerReference().value().toString()),
                                now);
                stock.saveReservationCommit(mutation, reservation.id());
                if (stock.physicalTotal(
                                mutation.balance().warehouseId(),
                                mutation.balance().variantId())
                        != mutation.balance().onHand().value()) {
                    throw new InventoryLedgerMismatchException();
                }
            }
            return transition;
        });
    }

    @Override
    @Transactional
    public InventoryReservation release(ReservationCommandRequest request) {
        return transition(request, RELEASE_OPERATION, (reservation, now) -> {
            var transition = reservation.release(new ReservationEventId(ids.next()), now);
            var byKey = byKey(stock.lockAll(keys(reservation.lines())));
            for (var line : reservation.lines().values()) {
                stock.saveBalance(balance(byKey, line).release(line.quantity()), now);
            }
            return transition;
        });
    }

    private InventoryReservation transition(
            ReservationCommandRequest request,
            String operation,
            BiFunction<InventoryReservation, java.time.Instant, ReservationTransition> action) {
        var owner = new ReservationOwnerReference(request.ownerReference());
        var reservationId = new ReservationId(request.reservationId());
        var scope = scope(owner);
        var fingerprint = InventoryCommandFingerprint.of(owner.value(), reservationId.value());
        var replay = idempotency.claim(scope, request.idempotencyKey(), operation, fingerprint);
        if (replay.isPresent()) {
            return replay(new ReservationEventId(replay.orElseThrow()), owner);
        }
        var current = requireOwned(reservations.lock(reservationId), owner);
        var transition = action.apply(current, clock.instant());
        reservations.update(transition);
        complete(scope, request.idempotencyKey(), transition);
        return transition.reservation();
    }

    private void complete(String scope, String key, ReservationTransition transition) {
        reservations.insertCommandResult(transition);
        idempotency.complete(scope, key, transition.event().id().value());
    }

    private InventoryReservation replay(ReservationEventId eventId, ReservationOwnerReference owner) {
        return requireOwned(reservations.findCommandResult(eventId), owner);
    }

    private static InventoryReservation requireOwned(
            java.util.Optional<InventoryReservation> reservation, ReservationOwnerReference owner) {
        return reservation
                .filter(found -> found.ownerReference().equals(owner))
                .orElseThrow(ReservationNotFoundException::new);
    }

    private void verifyActiveVariants(ReservationLines lines) {
        var requested =
                lines.values().stream().map(line -> line.variantId().value()).collect(Collectors.toUnmodifiableSet());
        if (!catalog.findActiveVariantIds(requested).equals(requested)) {
            throw new InventoryVariantNotActiveException();
        }
    }

    private static ReservationLines toLines(CreateInventoryReservationRequest request) {
        return ReservationLines.of(request.lines().stream()
                .map(line -> new ReservationLine(
                        new WarehouseId(line.warehouseId()),
                        new CatalogVariantId(line.variantId()),
                        new StockQuantity(line.quantity())))
                .toList());
    }

    private static String createFingerprint(ReservationOwnerReference owner, ReservationLines lines) {
        var components = lines.values().stream()
                .sorted((left, right) -> key(left.warehouseId(), left.variantId())
                        .compareTo(key(right.warehouseId(), right.variantId())))
                .flatMap(line -> Arrays.stream(new Object[] {
                    line.warehouseId().value(),
                    line.variantId().value(),
                    line.quantity().value()
                }))
                .toArray();
        var withOwner = new Object[components.length + 1];
        withOwner[0] = owner.value();
        System.arraycopy(components, 0, withOwner, 1, components.length);
        return InventoryCommandFingerprint.of(withOwner);
    }

    private static java.util.List<InventoryBalanceKey> keys(ReservationLines lines) {
        return lines.values().stream()
                .map(line -> key(line.warehouseId(), line.variantId()))
                .toList();
    }

    private static java.util.Map<InventoryBalanceKey, InventoryBalance> byKey(
            java.util.List<InventoryBalance> balances) {
        return balances.stream()
                .collect(Collectors.toMap(
                        balance -> key(balance.warehouseId(), balance.variantId()), balance -> balance));
    }

    private static InventoryBalance balance(
            java.util.Map<InventoryBalanceKey, InventoryBalance> balances, ReservationLine line) {
        return requireNonNull(balances.get(key(line.warehouseId(), line.variantId())));
    }

    private static InventoryBalanceKey key(WarehouseId warehouseId, CatalogVariantId variantId) {
        return new InventoryBalanceKey(warehouseId, variantId);
    }

    private static String scope(ReservationOwnerReference owner) {
        return owner.value().toString();
    }
}
