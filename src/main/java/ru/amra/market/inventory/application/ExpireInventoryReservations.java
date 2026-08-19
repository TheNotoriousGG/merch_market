package ru.amra.market.inventory.application;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.inventory.application.port.InventoryIdGenerator;
import ru.amra.market.inventory.application.port.InventoryJobLease;
import ru.amra.market.inventory.application.port.InventoryReservationRepository;
import ru.amra.market.inventory.application.port.InventoryStockRepository;
import ru.amra.market.inventory.application.port.InventoryStockRepository.InventoryBalanceKey;
import ru.amra.market.inventory.domain.InventoryBalance;
import ru.amra.market.inventory.domain.InventoryReservation;
import ru.amra.market.inventory.domain.ReservationEventId;
import ru.amra.market.inventory.domain.ReservationLine;

/** Bounded cross-instance expiry use case coordinated by a PostgreSQL lease. */
@Service
public class ExpireInventoryReservations {
    static final String JOB_NAME = "inventory-reservation-expiry";

    private final InventoryJobLease lease;
    private final InventoryReservationRepository reservations;
    private final InventoryStockRepository stock;
    private final InventoryIdGenerator ids;

    public ExpireInventoryReservations(
            InventoryJobLease lease,
            InventoryReservationRepository reservations,
            InventoryStockRepository stock,
            InventoryIdGenerator ids) {
        this.lease = lease;
        this.reservations = reservations;
        this.stock = stock;
        this.ids = ids;
    }

    /** Expires one bounded batch, or returns zero while another instance owns the lease. */
    @Transactional
    public int runBatch(String ownerInstanceId, Duration leaseDuration, int batchSize) {
        if (ownerInstanceId == null
                || ownerInstanceId.isBlank()
                || ownerInstanceId.length() > 128
                || leaseDuration == null
                || leaseDuration.isZero()
                || leaseDuration.isNegative()
                || batchSize < 1
                || batchSize > 1000) {
            throw new IllegalArgumentException("Inventory expiry execution parameters are outside safe bounds");
        }
        if (!lease.tryAcquire(JOB_NAME, ownerInstanceId, leaseDuration)) {
            return 0;
        }
        var databaseNow = reservations.databaseTime();
        var due = reservations.lockExpiredBatch(databaseNow, batchSize);
        for (var reservation : due) {
            expire(reservation, databaseNow);
        }
        return due.size();
    }

    private void expire(InventoryReservation reservation, Instant databaseNow) {
        var transition = reservation.expire(new ReservationEventId(ids.next()), databaseNow);
        var balances = stock.lockAll(reservation.lines().values().stream()
                .map(line -> new InventoryBalanceKey(line.warehouseId(), line.variantId()))
                .toList());
        var byKey = balances.stream()
                .collect(Collectors.toMap(
                        balance -> new InventoryBalanceKey(balance.warehouseId(), balance.variantId()),
                        balance -> balance));
        for (var line : reservation.lines().values()) {
            stock.saveBalance(balance(byKey, line).release(line.quantity()), databaseNow);
        }
        reservations.update(transition);
        reservations.insertCommandResult(transition);
    }

    private static InventoryBalance balance(
            java.util.Map<InventoryBalanceKey, InventoryBalance> balances, ReservationLine line) {
        var balance = balances.get(new InventoryBalanceKey(line.warehouseId(), line.variantId()));
        if (balance == null) {
            throw new InventoryBalanceNotFoundException();
        }
        return balance;
    }
}
