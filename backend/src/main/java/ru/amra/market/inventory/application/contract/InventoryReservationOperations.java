package ru.amra.market.inventory.application.contract;

import java.util.UUID;
import ru.amra.market.inventory.domain.InventoryReservation;

/** Inventory reservation contract for future ordering composition. */
public interface InventoryReservationOperations {
    /** Creates or replays an all-or-nothing reservation. */
    InventoryReservation create(CreateInventoryReservationRequest request);

    /** Creates or replays a reservation and exposes only its opaque identifier to other modules. */
    UUID createId(CreateInventoryReservationRequest request);

    /** Reads a reservation only inside its opaque owner scope. */
    InventoryReservation get(UUID ownerReference, UUID reservationId);

    /** Extends a live reservation exactly once or replays the original result. */
    InventoryReservation extend(ReservationCommandRequest request);

    /** Commits every reserved line atomically or replays the original result. */
    InventoryReservation commit(ReservationCommandRequest request);

    /** Releases every reserved line atomically or replays the original result. */
    InventoryReservation release(ReservationCommandRequest request);
}
