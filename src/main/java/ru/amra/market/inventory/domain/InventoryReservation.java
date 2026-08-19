package ru.amra.market.inventory.domain;

import java.time.Duration;
import java.time.Instant;

/** Immutable all-or-nothing reservation lifecycle aggregate. */
public final class InventoryReservation {

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    private final ReservationId id;
    private final ReservationOwnerReference ownerReference;
    private final ReservationLines lines;
    private final ReservationStatus status;
    private final Instant expiresAt;
    private final int extensionCount;
    private final long version;

    private InventoryReservation(
            ReservationId id,
            ReservationOwnerReference ownerReference,
            ReservationLines lines,
            ReservationStatus status,
            Instant expiresAt,
            int extensionCount,
            long version) {
        if (id == null || ownerReference == null || lines == null || status == null || expiresAt == null) {
            throw invalid("Reservation metadata must not be null");
        }
        if (extensionCount < 0 || extensionCount > 1 || version < 0) {
            throw invalid("Reservation extension count and version are outside their bounds");
        }
        this.id = id;
        this.ownerReference = ownerReference;
        this.lines = lines;
        this.status = status;
        this.expiresAt = expiresAt;
        this.extensionCount = extensionCount;
        this.version = version;
    }

    /** Creates an active reservation and its initial event. */
    public static ReservationTransition create(
            ReservationId id,
            ReservationEventId eventId,
            ReservationOwnerReference ownerReference,
            ReservationLines lines,
            Instant now,
            Duration ttl) {
        requirePositiveTtl(ttl);
        if (now == null) {
            throw invalid("Reservation creation time must not be null");
        }
        var expiresAt = checkedPlus(now, ttl);
        var reservation =
                new InventoryReservation(id, ownerReference, lines, ReservationStatus.ACTIVE, expiresAt, 0, 0);
        return new ReservationTransition(
                reservation,
                new ReservationEvent(
                        eventId,
                        id,
                        ReservationEventType.CREATED,
                        null,
                        ReservationStatus.ACTIVE,
                        null,
                        expiresAt,
                        now));
    }

    /** Restores a persisted reservation without producing an event. */
    public static InventoryReservation restore(
            ReservationId id,
            ReservationOwnerReference ownerReference,
            ReservationLines lines,
            ReservationStatus status,
            Instant expiresAt,
            int extensionCount,
            long version) {
        return new InventoryReservation(id, ownerReference, lines, status, expiresAt, extensionCount, version);
    }

    /** Extends a live reservation exactly once by the configured TTL. */
    public ReservationTransition extend(ReservationEventId eventId, Instant now, Duration ttl) {
        requireActiveBeforeExpiry(now);
        requirePositiveTtl(ttl);
        if (extensionCount != 0) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.RESERVATION_EXTENSION_LIMIT_REACHED, "Reservation has already been extended");
        }
        var extendedExpiry = checkedPlus(expiresAt, ttl);
        if (!extendedExpiry.isAfter(expiresAt)) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.RESERVATION_EXTENSION_NOT_STRICT,
                    "Extended expiry must be later than current expiry");
        }
        return transition(eventId, ReservationEventType.EXTENDED, ReservationStatus.ACTIVE, extendedExpiry, 1, now);
    }

    /** Commits a live reservation. Durable application idempotency handles command replay. */
    public ReservationTransition commit(ReservationEventId eventId, Instant now) {
        requireActiveBeforeExpiry(now);
        return transition(
                eventId, ReservationEventType.COMMITTED, ReservationStatus.COMMITTED, expiresAt, extensionCount, now);
    }

    /** Releases a live reservation. Durable application idempotency handles command replay. */
    public ReservationTransition release(ReservationEventId eventId, Instant now) {
        requireActiveBeforeExpiry(now);
        return transition(
                eventId, ReservationEventType.RELEASED, ReservationStatus.RELEASED, expiresAt, extensionCount, now);
    }

    /** Expires a due reservation. Durable application idempotency handles command replay. */
    public ReservationTransition expire(ReservationEventId eventId, Instant now) {
        requireActive();
        requireNow(now);
        if (now.isBefore(expiresAt)) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.RESERVATION_NOT_EXPIRED, "Reservation cannot expire before its deadline");
        }
        return transition(
                eventId, ReservationEventType.EXPIRED, ReservationStatus.EXPIRED, expiresAt, extensionCount, now);
    }

    public ReservationId id() {
        return id;
    }

    public ReservationOwnerReference ownerReference() {
        return ownerReference;
    }

    public ReservationLines lines() {
        return lines;
    }

    public ReservationStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public int extensionCount() {
        return extensionCount;
    }

    public long version() {
        return version;
    }

    /** Returns whether the deadline has elapsed independently of worker progress. */
    public boolean isExpiredAt(Instant now) {
        requireNow(now);
        return !now.isBefore(expiresAt);
    }

    private ReservationTransition transition(
            ReservationEventId eventId,
            ReservationEventType type,
            ReservationStatus targetStatus,
            Instant targetExpiry,
            int targetExtensionCount,
            Instant now) {
        var next = new InventoryReservation(
                id, ownerReference, lines, targetStatus, targetExpiry, targetExtensionCount, incrementVersion());
        return new ReservationTransition(
                next, new ReservationEvent(eventId, id, type, status, targetStatus, expiresAt, targetExpiry, now));
    }

    private void requireActiveBeforeExpiry(Instant now) {
        requireActive();
        requireNow(now);
        if (!now.isBefore(expiresAt)) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.RESERVATION_EXPIRED, "Reservation deadline has elapsed");
        }
    }

    private void requireActive() {
        if (status != ReservationStatus.ACTIVE) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.RESERVATION_STATE_CONFLICT,
                    "Reservation is already in a conflicting terminal state");
        }
    }

    private long incrementVersion() {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException exception) {
            throw new InventoryInvariantViolation(
                    InventoryInvariant.VERSION_OVERFLOW, "Reservation version overflowed");
        }
    }

    private static Instant checkedPlus(Instant instant, Duration duration) {
        try {
            return instant.plus(duration);
        } catch (RuntimeException exception) {
            throw invalid("Reservation expiry is outside the supported instant range");
        }
    }

    private static void requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw invalid("Reservation TTL must be positive");
        }
    }

    private static void requireNow(Instant now) {
        if (now == null) {
            throw invalid("Reservation transition time must not be null");
        }
    }

    private static InventoryInvariantViolation invalid(String message) {
        return new InventoryInvariantViolation(InventoryInvariant.INVALID_RESERVATION, message);
    }
}
