package ru.amra.market.inventory.application.port;

import java.util.Optional;
import ru.amra.market.inventory.domain.InventoryReservation;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.ReservationTransition;

/** Persistence boundary for reservation roots, lines and events. */
public interface InventoryReservationRepository {
    /** Inserts a reservation creation atomically. */
    void insert(ReservationTransition creation);

    /** Restores a reservation by identity. */
    Optional<InventoryReservation> find(ReservationId id);
}
