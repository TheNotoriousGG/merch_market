package ru.amra.market.inventory.application.port;

import java.util.Optional;
import ru.amra.market.inventory.domain.InventoryReservation;
import ru.amra.market.inventory.domain.ReservationEventId;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.ReservationTransition;

/** Persistence boundary for reservation roots, lines and events. */
public interface InventoryReservationRepository {
    /** Inserts a reservation creation atomically. */
    void insert(ReservationTransition creation);

    /** Restores a reservation by identity. */
    Optional<InventoryReservation> find(ReservationId id);

    /** Locks and restores a reservation root for a lifecycle transition. */
    Optional<InventoryReservation> lock(ReservationId id);

    /** Optimistically stores a transition and appends its event. */
    void update(ReservationTransition transition);

    /** Stores the immutable exact result of a reservation command. */
    void insertCommandResult(ReservationTransition transition);

    /** Restores the exact reservation snapshot originally returned for an event. */
    Optional<InventoryReservation> findCommandResult(ReservationEventId eventId);
}
