package ru.amra.market.inventory.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.amra.market.inventory.application.port.InventoryReservationRepository;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.InventoryReservation;
import ru.amra.market.inventory.domain.ReservationEvent;
import ru.amra.market.inventory.domain.ReservationEventId;
import ru.amra.market.inventory.domain.ReservationId;
import ru.amra.market.inventory.domain.ReservationLine;
import ru.amra.market.inventory.domain.ReservationLines;
import ru.amra.market.inventory.domain.ReservationOwnerReference;
import ru.amra.market.inventory.domain.ReservationStatus;
import ru.amra.market.inventory.domain.ReservationTransition;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;

/** JDBC reservation root/line/event adapter. */
@Repository
class JdbcInventoryReservationRepository implements InventoryReservationRepository {
    private final JdbcTemplate jdbc;

    JdbcInventoryReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Instant databaseTime() {
        return java.util.Objects.requireNonNull(jdbc.queryForObject("select clock_timestamp()", Instant.class));
    }

    @Override
    public void insert(ReservationTransition creation) {
        var reservation = creation.reservation();
        jdbc.update(
                """
                insert into inventory_reservations (
                    id, owner_reference, status, expires_at, extension_count, version
                ) values (?, ?, ?, ?, ?, ?)
                """,
                reservation.id().value(),
                reservation.ownerReference().value(),
                reservation.status().name(),
                Timestamp.from(reservation.expiresAt()),
                reservation.extensionCount(),
                reservation.version());
        for (var line : reservation.lines().values()) {
            jdbc.update(
                    """
                    insert into inventory_reservation_lines (
                        reservation_id, warehouse_id, variant_id, quantity
                    ) values (?, ?, ?, ?)
                    """,
                    reservation.id().value(),
                    line.warehouseId().value(),
                    line.variantId().value(),
                    line.quantity().value());
        }
        insertEvent(creation.event());
    }

    @Override
    public Optional<InventoryReservation> find(ReservationId id) {
        return find(id, "");
    }

    @Override
    public Optional<InventoryReservation> lock(ReservationId id) {
        return find(id, " for update");
    }

    @Override
    public List<InventoryReservation> lockExpiredBatch(Instant databaseNow, int limit) {
        return jdbc.query(
                """
                select id, owner_reference, status, expires_at, extension_count, version
                from inventory_reservations
                where status = 'ACTIVE' and expires_at <= ?
                order by expires_at, id
                limit ?
                for update skip locked
                """,
                (result, row) -> {
                    var id = new ReservationId(result.getObject("id", java.util.UUID.class));
                    return InventoryReservation.restore(
                            id,
                            new ReservationOwnerReference(result.getObject("owner_reference", java.util.UUID.class)),
                            lines(id),
                            ReservationStatus.valueOf(result.getString("status")),
                            result.getTimestamp("expires_at").toInstant(),
                            result.getInt("extension_count"),
                            result.getLong("version"));
                },
                Timestamp.from(databaseNow),
                limit);
    }

    @Override
    public void update(ReservationTransition transition) {
        var reservation = transition.reservation();
        var updated = jdbc.update(
                """
                update inventory_reservations
                set status = ?, expires_at = ?, extension_count = ?, terminal_at = ?, version = ?
                where id = ? and version = ?
                """,
                reservation.status().name(),
                Timestamp.from(reservation.expiresAt()),
                reservation.extensionCount(),
                reservation.status() == ReservationStatus.ACTIVE
                        ? null
                        : Timestamp.from(transition.event().occurredAt()),
                reservation.version(),
                reservation.id().value(),
                reservation.version() - 1);
        if (updated != 1) {
            throw new ru.amra.market.inventory.application.StaleInventoryVersionException();
        }
        insertEvent(transition.event());
    }

    @Override
    public void insertCommandResult(ReservationTransition transition) {
        var reservation = transition.reservation();
        jdbc.update(
                """
                insert into inventory_reservation_command_results (
                    event_id, reservation_id, status, expires_at,
                    extension_count, reservation_version
                ) values (?, ?, ?, ?, ?, ?)
                """,
                transition.event().id().value(),
                reservation.id().value(),
                reservation.status().name(),
                Timestamp.from(reservation.expiresAt()),
                reservation.extensionCount(),
                reservation.version());
    }

    @Override
    public Optional<InventoryReservation> findCommandResult(ReservationEventId eventId) {
        var results = jdbc.query(
                """
                select result.reservation_id, root.owner_reference, result.status,
                       result.expires_at, result.extension_count, result.reservation_version
                from inventory_reservation_command_results result
                join inventory_reservations root on root.id = result.reservation_id
                where result.event_id = ?
                """,
                (result, row) -> {
                    var reservationId = new ReservationId(result.getObject("reservation_id", java.util.UUID.class));
                    return InventoryReservation.restore(
                            reservationId,
                            new ReservationOwnerReference(result.getObject("owner_reference", java.util.UUID.class)),
                            lines(reservationId),
                            ReservationStatus.valueOf(result.getString("status")),
                            result.getTimestamp("expires_at").toInstant(),
                            result.getInt("extension_count"),
                            result.getLong("reservation_version"));
                },
                eventId.value());
        return results.stream().findFirst();
    }

    private Optional<InventoryReservation> find(ReservationId id, String lockClause) {
        var roots = jdbc.query(
                """
                select owner_reference, status, expires_at, extension_count, version
                from inventory_reservations where id = ?
                """ + lockClause,
                (result, row) -> InventoryReservation.restore(
                        id,
                        new ReservationOwnerReference(result.getObject("owner_reference", java.util.UUID.class)),
                        lines(id),
                        ReservationStatus.valueOf(result.getString("status")),
                        result.getTimestamp("expires_at").toInstant(),
                        result.getInt("extension_count"),
                        result.getLong("version")),
                id.value());
        return roots.stream().findFirst();
    }

    private void insertEvent(ReservationEvent event) {
        jdbc.update(
                """
                insert into inventory_reservation_events (
                    id, reservation_id, event_type, previous_status, current_status,
                    previous_expires_at, current_expires_at, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.id().value(),
                event.reservationId().value(),
                event.type().name(),
                event.previousStatus() == null ? null : event.previousStatus().name(),
                event.currentStatus().name(),
                event.previousExpiresAt() == null ? null : Timestamp.from(event.previousExpiresAt()),
                Timestamp.from(event.currentExpiresAt()),
                Timestamp.from(event.occurredAt()));
    }

    private ReservationLines lines(ReservationId id) {
        return ReservationLines.of(jdbc.query(
                """
                select warehouse_id, variant_id, quantity
                from inventory_reservation_lines
                where reservation_id = ?
                order by warehouse_id, variant_id
                """,
                (result, row) -> new ReservationLine(
                        new WarehouseId(result.getObject("warehouse_id", java.util.UUID.class)),
                        new CatalogVariantId(result.getObject("variant_id", java.util.UUID.class)),
                        new StockQuantity(result.getLong("quantity"))),
                id.value()));
    }
}
