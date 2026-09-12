package ru.amra.market.ordering;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.amra.market.inventory.application.contract.CreateInventoryReservationRequest;
import ru.amra.market.inventory.application.contract.InventoryReservationOperations;
import ru.amra.market.inventory.application.contract.ReservationCommandRequest;
import ru.amra.market.inventory.application.contract.ReservationRequestLine;
import ru.amra.market.pricing.PricingEngine;
import ru.amra.market.pricing.PricingService;

/** Atomic cart checkout and immutable customer order snapshots. */
@Service
public class OrderingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PricingService pricing;
    private final InventoryReservationOperations reservations;
    private final Clock clock;

    public OrderingService(
            NamedParameterJdbcTemplate jdbc,
            PricingService pricing,
            InventoryReservationOperations reservations,
            Clock clock) {
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.reservations = reservations;
        this.clock = clock;
    }

    /** Revalidates, reserves and confirms one cart exactly once for an owner/key pair. */
    @Transactional
    public Order checkout(Owner owner, String key, Checkout checkout) {
        jdbc.query(
                "select pg_advisory_xact_lock(hashtextextended(:scope, 0))",
                Map.of("scope", owner.type() + ":" + owner.id() + ":" + key),
                result -> {
                    result.next();
                    return Boolean.TRUE;
                });
        var fingerprint = fingerprint(checkout);
        var replay = jdbc.query(
                """
                select command.request_fingerprint, orders.public_number
                from ordering_checkout_commands command
                join customer_orders orders on orders.id = command.order_id
                where command.owner_type = :type and command.owner_id = :owner and command.idempotency_key = :key
                """,
                new MapSqlParameterSource(owner.parameters()).addValue("key", key),
                (result, row) -> new Replay(result.getString(1), result.getString(2)));
        if (!replay.isEmpty()) {
            if (!replay.getFirst().fingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key уже использован");
            }
            return get(owner, replay.getFirst().publicNumber(), null);
        }

        var carts = jdbc.query(
                "select id, version from customer_carts where owner_type = :type and owner_id = :owner for update",
                owner.parameters(),
                (result, row) -> new Cart(result.getObject(1, UUID.class), result.getLong(2)));
        if (carts.isEmpty() || carts.getFirst().version() != checkout.cartVersion()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "Корзина уже изменилась");
        }
        var cart = carts.getFirst();
        var rawLines = jdbc.query(
                """
                select item.variant_id, item.quantity, variant.sku, variant.label, product.name,
                       coalesce(sum(balance.on_hand - balance.reserved), 0) available
                from customer_cart_items item
                join catalog_product_variants variant on variant.id = item.variant_id and variant.status = 'ACTIVE'
                join catalog_products product on product.id = variant.product_id and product.status = 'ACTIVE'
                left join inventory_balances balance on balance.variant_id = variant.id
                where item.cart_id = :cart
                group by item.variant_id, item.quantity, variant.sku, variant.label, product.name, item.added_at
                order by item.added_at, item.variant_id
                """,
                Map.of("cart", cart.id()),
                (result, row) -> new RawLine(
                        result.getObject(1, UUID.class),
                        result.getInt(2),
                        result.getString(3),
                        result.getString(4),
                        result.getString(5),
                        result.getLong(6)));
        if (rawLines.isEmpty() || rawLines.stream().anyMatch(line -> line.available() < line.quantity())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Корзина пуста или товара недостаточно");
        }
        var lines = rawLines.stream().map(this::quote).toList();
        var warehouse = Objects.requireNonNull(jdbc.getJdbcTemplate()
                .queryForObject("select id from amra_shop.inventory_warehouses where code = 'PRIMARY'", UUID.class));
        var orderId = nextId();
        reservations.create(new CreateInventoryReservationRequest(
                orderId,
                "checkout-reserve-" + key,
                rawLines.stream()
                        .map(line -> new ReservationRequestLine(warehouse, line.variantId(), line.quantity()))
                        .toList()));
        var reservationId = Objects.requireNonNull(jdbc.getJdbcTemplate()
                .queryForObject(
                        "select id from amra_shop.inventory_reservations where owner_reference = ?",
                        UUID.class,
                        orderId));
        reservations.commit(new ReservationCommandRequest(orderId, reservationId, "checkout-commit-" + key));

        var now = clock.instant();
        var publicNumber =
                "AMR-" + nextId().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        var token = owner.customer() ? null : guestToken(orderId, owner.id());
        var subtotal = lines.stream()
                .mapToLong(line -> line.unitPrice() * line.quantity())
                .sum();
        var discount = lines.stream().mapToLong(Line::discount).sum();
        insertOrder(orderId, publicNumber, owner, reservationId, token, subtotal, discount, checkout, now);
        insertLines(orderId, lines);
        insertEventAndOutbox(orderId, publicNumber, now);
        jdbc.update("delete from customer_cart_items where cart_id = :cart", Map.of("cart", cart.id()));
        jdbc.update(
                "update customer_carts set version = version + 1, updated_at = :now where id = :cart",
                Map.of("cart", cart.id(), "now", Timestamp.from(now)));
        jdbc.update(
                """
                insert into ordering_checkout_commands(
                    owner_type, owner_id, idempotency_key, request_fingerprint, order_id, created_at)
                values (:type, :owner, :key, :fingerprint, :order, :now)
                """,
                new MapSqlParameterSource(owner.parameters())
                        .addValue("key", key)
                        .addValue("fingerprint", fingerprint)
                        .addValue("order", orderId)
                        .addValue("now", Timestamp.from(now)));
        return get(owner, publicNumber, token);
    }

    /** Reads an order using its owner session or a scoped guest token. */
    @Transactional(readOnly = true)
    public Order get(Owner owner, String publicNumber, @Nullable String suppliedToken) {
        var orders = jdbc.query(
                """
                select id, public_number, owner_type, owner_id, status, subtotal_minor, discount_minor,
                       total_minor, email, recipient_name, phone, postal_code, city, street, apartment,
                       guest_access_token_hash, created_at
                from customer_orders where public_number = :number
                """,
                Map.of("number", publicNumber),
                (result, row) -> new OrderRecord(
                        result.getObject(1, UUID.class),
                        result.getString(2),
                        result.getString(3),
                        result.getObject(4, UUID.class),
                        result.getString(5),
                        result.getLong(6),
                        result.getLong(7),
                        result.getLong(8),
                        result.getString(9),
                        result.getString(10),
                        result.getString(11),
                        result.getString(12),
                        result.getString(13),
                        result.getString(14),
                        result.getString(15),
                        result.getString(16),
                        result.getTimestamp(17).toInstant()));
        if (orders.isEmpty()) {
            throw notFound();
        }
        var found = orders.getFirst();
        var owned = found.ownerType().equals(owner.type()) && found.ownerId().equals(owner.id());
        var tokenAccepted = found.guestTokenHash() != null
                && suppliedToken != null
                && MessageDigest.isEqual(bytes(found.guestTokenHash()), bytes(hash(suppliedToken)));
        if (!owned && !tokenAccepted) {
            throw notFound();
        }
        return new Order(
                found.id(),
                found.publicNumber(),
                found.status(),
                found.subtotal(),
                found.discount(),
                found.total(),
                found.email(),
                found.recipient(),
                found.phone(),
                found.postalCode(),
                found.city(),
                found.street(),
                found.apartment(),
                found.ownerType().equals("GUEST") ? guestToken(found.id(), found.ownerId()) : null,
                loadLines(found.id()),
                found.createdAt());
    }

    private Line quote(RawLine raw) {
        PricingEngine.LineResult quoted = pricing.quote(raw.variantId(), raw.variantId(), raw.quantity());
        return new Line(
                raw.variantId(),
                raw.sku(),
                raw.name(),
                raw.label(),
                raw.quantity(),
                quoted.unitPriceMinor(),
                quoted.discountMinor(),
                quoted.totalMinor(),
                quoted.promotion() == null ? null : quoted.promotion().name());
    }

    private void insertOrder(
            UUID id,
            String number,
            Owner owner,
            UUID reservationId,
            @Nullable String token,
            long subtotal,
            long discount,
            Checkout checkout,
            Instant now) {
        jdbc.update(
                """
                insert into customer_orders(
                    id, public_number, owner_type, owner_id, status, reservation_id, guest_access_token_hash,
                    currency, subtotal_minor, discount_minor, total_minor, email, recipient_name, phone,
                    postal_code, city, street, apartment, created_at, updated_at)
                values (:id, :number, :type, :owner, 'CONFIRMED', :reservation, :token, 'RUB', :subtotal,
                    :discount, :total, :email, :recipient, :phone, :postal, :city, :street, :apartment, :now, :now)
                """,
                new MapSqlParameterSource(owner.parameters())
                        .addValue("id", id)
                        .addValue("number", number)
                        .addValue("reservation", reservationId)
                        .addValue("token", token == null ? null : hash(token))
                        .addValue("subtotal", subtotal)
                        .addValue("discount", discount)
                        .addValue("total", subtotal - discount)
                        .addValue("email", checkout.email())
                        .addValue("recipient", checkout.recipientName())
                        .addValue("phone", checkout.phone())
                        .addValue("postal", checkout.postalCode())
                        .addValue("city", checkout.city())
                        .addValue("street", checkout.street())
                        .addValue("apartment", checkout.apartment())
                        .addValue("now", Timestamp.from(now)));
    }

    private void insertLines(UUID orderId, List<Line> lines) {
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            jdbc.update(
                    """
                    insert into customer_order_lines(order_id, line_number, variant_id, sku, product_name,
                        variant_label, quantity, unit_price_minor, discount_minor, total_minor, promotion_name)
                    values (:order, :number, :variant, :sku, :name, :label, :quantity, :price,
                        :discount, :total, :promotion)
                    """,
                    new MapSqlParameterSource()
                            .addValue("order", orderId)
                            .addValue("number", index + 1)
                            .addValue("variant", line.variantId())
                            .addValue("sku", line.sku())
                            .addValue("name", line.name())
                            .addValue("label", line.label())
                            .addValue("quantity", line.quantity())
                            .addValue("price", line.unitPrice())
                            .addValue("discount", line.discount())
                            .addValue("total", line.total())
                            .addValue("promotion", line.promotionName()));
        }
    }

    private void insertEventAndOutbox(UUID orderId, String publicNumber, Instant now) {
        var timestamp = Timestamp.from(now);
        jdbc.update("""
                insert into ordering_events(id, order_id, event_type, to_status, occurred_at)
                values (:id, :order, 'ORDER_CONFIRMED', 'CONFIRMED', :now)
                """, Map.of("id", nextId(), "order", orderId, "now", timestamp));
        jdbc.update(
                """
                insert into transactional_outbox(id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
                values (:id, 'ORDER', :order, 'ORDER_CONFIRMED', cast(:payload as jsonb), :now)
                """,
                Map.of(
                        "id",
                        nextId(),
                        "order",
                        orderId,
                        "payload",
                        "{\"publicNumber\":\"" + publicNumber + "\"}",
                        "now",
                        timestamp));
    }

    private List<Line> loadLines(UUID orderId) {
        return jdbc.query(
                """
                select variant_id, sku, product_name, variant_label, quantity, unit_price_minor,
                       discount_minor, total_minor, promotion_name
                from customer_order_lines where order_id = :order order by line_number
                """,
                Map.of("order", orderId),
                (result, row) -> new Line(
                        result.getObject(1, UUID.class),
                        result.getString(2),
                        result.getString(3),
                        result.getString(4),
                        result.getInt(5),
                        result.getLong(6),
                        result.getLong(7),
                        result.getLong(8),
                        result.getString(9)));
    }

    private UUID nextId() {
        return Objects.requireNonNull(jdbc.getJdbcTemplate().queryForObject("select uuidv7()", UUID.class));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден");
    }

    private static String guestToken(UUID orderId, UUID ownerId) {
        var buffer = ByteBuffer.allocate(32)
                .putLong(orderId.getMostSignificantBits())
                .putLong(orderId.getLeastSignificantBits())
                .putLong(ownerId.getMostSignificantBits())
                .putLong(ownerId.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(buffer.array()));
    }

    private static String fingerprint(Checkout value) {
        return hash(value.toString());
    }

    private static String hash(String value) {
        return HexFormat.of().formatHex(digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Owner(String type, UUID id) {
        public boolean customer() {
            return "CUSTOMER".equals(type);
        }

        Map<String, Object> parameters() {
            return Map.of("type", type, "owner", id);
        }
    }

    public record Checkout(
            long cartVersion,
            String email,
            String recipientName,
            String phone,
            String postalCode,
            String city,
            String street,
            @Nullable String apartment) {}

    public record Order(
            UUID id,
            String publicNumber,
            String status,
            long subtotal,
            long discount,
            long total,
            String email,
            String recipient,
            String phone,
            String postalCode,
            String city,
            String street,
            @Nullable String apartment,
            @Nullable String guestAccessToken,
            List<Line> lines,
            Instant createdAt) {}

    public record Line(
            UUID variantId,
            String sku,
            String name,
            String label,
            int quantity,
            long unitPrice,
            long discount,
            long total,
            @Nullable String promotionName) {}

    private record Cart(UUID id, long version) {}

    private record RawLine(UUID variantId, int quantity, String sku, String label, String name, long available) {}

    private record Replay(String fingerprint, String publicNumber) {}

    private record OrderRecord(
            UUID id,
            String publicNumber,
            String ownerType,
            UUID ownerId,
            String status,
            long subtotal,
            long discount,
            long total,
            String email,
            String recipient,
            String phone,
            String postalCode,
            String city,
            String street,
            @Nullable String apartment,
            @Nullable String guestTokenHash,
            Instant createdAt) {}
}
