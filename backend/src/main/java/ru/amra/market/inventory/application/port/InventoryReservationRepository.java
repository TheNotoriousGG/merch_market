package ru.amra.market.inventory.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ru.amra.market.inventory.domain.InventoryReservation;
import ru.amra.market.inventory.domain.ReservationEventId;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.ReservationTransition;

/** Persistence boundary for reservation roots, lines and events. */
public interface InventoryReservationRepository {
    /** Returns database wall-clock time used for expiry decisions. */
    Instant databaseTime();

    /** Inserts a reservation creation atomically. */
    void insert(ReservationTransition creation);

    /** Restores a reservation by identity. */
    Optional<InventoryReservation> find(ReservationId id);

    /** Locks and restores a reservation root for a lifecycle transition. */
    Optional<InventoryReservation> lock(ReservationId id);

    /** Locks at most {@code limit} due active reservations while skipping rows held elsewhere. */
    List<InventoryReservation> lockExpiredBatch(Instant databaseNow, int limit);

    /** Optimistically stores a transition and appends its event. */
    void update(ReservationTransition transition);

    /** Stores the immutable exact result of a reservation command. */
    void insertCommandResult(ReservationTransition transition);

    /** Restores the exact reservation snapshot originally returned for an event. */
    Optional<InventoryReservation> findCommandResult(ReservationEventId eventId);
}
