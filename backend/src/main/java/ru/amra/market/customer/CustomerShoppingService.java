package ru.amra.market.customer;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Persistent guest and authenticated shopping state. */
@Service
class CustomerShoppingService {
    static final String GUEST_COOKIE = "AMRA_GUEST";
    private static final Duration GUEST_RETENTION = Duration.ofDays(30);
    private static final Duration CUSTOMER_RETENTION = Duration.ofDays(90);

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final boolean secureCookie;

    CustomerShoppingService(
            NamedParameterJdbcTemplate jdbc,
            Clock clock,
            @Value("${amra.customer.guest-cookie-secure:false}") boolean secureCookie) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.secureCookie = secureCookie;
    }

    ShoppingOwner owner(HttpServletRequest request, HttpServletResponse response) {
        var customerId = customerId(request);
        if (customerId.isPresent()) {
            return new ShoppingOwner("CUSTOMER", customerId.get());
        }
        var token = cookie(request, GUEST_COOKIE);
        if (token != null) {
            var guest = jdbc.query(
                    "select id from guest_profiles where token_hash = :hash and expires_at > :now",
                    Map.of("hash", hash(token), "now", Timestamp.from(clock.instant())),
                    (result, row) -> result.getObject("id", UUID.class));
            if (!guest.isEmpty()) {
                touchGuest(guest.getFirst());
                return new ShoppingOwner("GUEST", guest.getFirst());
            }
        }
        return createGuest(response);
    }

    Context context(ShoppingOwner owner) {
        var favorites =
                jdbc.query("""
                select product_id from customer_favorites
                where owner_type = :type and owner_id = :id order by created_at, product_id
                """, owner.parameters(), (result, row) -> result.getObject("product_id", UUID.class));
        return new Context(owner.customer(), owner.customer() ? owner.id() : null, Set.copyOf(favorites));
    }

    Profile profile(HttpServletRequest request) {
        var customerId = requireCustomer(request);
        var profiles = jdbc.query(
                "select id, phone, display_name, email, email_verified from customer_accounts where id = :id",
                Map.of("id", customerId),
                (result, row) -> new Profile(
                        result.getObject("id", UUID.class),
                        Objects.requireNonNull(result.getString("phone")),
                        result.getString("display_name"),
                        result.getString("email"),
                        result.getBoolean("email_verified"),
                        addresses(customerId)));
        return profiles.stream().findFirst().orElseThrow(() -> unauthorized());
    }

    @Transactional
    Profile updateProfile(HttpServletRequest request, @Nullable String displayName, @Nullable String email) {
        var customerId = requireCustomer(request);
        var currentEmail = jdbc.queryForObject(
                "select email from customer_accounts where id = :id", Map.of("id", customerId), String.class);
        jdbc.update(
                """
                update customer_accounts
                set display_name = :displayName, email = :email,
                    email_verified = case
                        when email is not distinct from cast(:email as varchar) then email_verified
                        else false
                    end,
                    updated_at = :now
                where id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", customerId)
                        .addValue("displayName", blankToNull(displayName))
                        .addValue("email", blankToNull(email))
                        .addValue("now", Timestamp.from(clock.instant())));
        // Explicit comparison documents that changing the email revokes verification.
        if (!Objects.equals(currentEmail, blankToNull(email))) {
            jdbc.update("update customer_accounts set email_verified = false where id = :id", Map.of("id", customerId));
        }
        return profile(request);
    }

    @Transactional
    Address createAddress(HttpServletRequest request, AddressDraft draft) {
        var customerId = requireCustomer(request);
        var id = nextId();
        var now = Timestamp.from(clock.instant());
        jdbc.update(
                """
                insert into customer_addresses(
                    id, customer_id, label, recipient_name, phone, postal_code,
                    city, street, apartment, created_at, updated_at)
                values (
                    :id, :customerId, :label, :recipient, :phone, :postalCode,
                    :city, :street, :apartment, :now, :now)
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("customerId", customerId)
                        .addValue("label", draft.label().trim())
                        .addValue("recipient", draft.recipientName().trim())
                        .addValue("phone", draft.phone())
                        .addValue("postalCode", draft.postalCode().trim())
                        .addValue("city", draft.city().trim())
                        .addValue("street", draft.street().trim())
                        .addValue("apartment", blankToNull(draft.apartment()))
                        .addValue("now", now));
        return new Address(
                id,
                draft.label().trim(),
                draft.recipientName().trim(),
                draft.phone(),
                draft.postalCode().trim(),
                draft.city().trim(),
                draft.street().trim(),
                blankToNull(draft.apartment()));
    }

    void deleteAddress(HttpServletRequest request, UUID addressId) {
        var changed = jdbc.update(
                "delete from customer_addresses where id = :address and customer_id = :customer",
                Map.of("address", addressId, "customer", requireCustomer(request)));
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Адрес не найден");
        }
    }

    void addFavorite(ShoppingOwner owner, UUID productId) {
        var exists = jdbc.queryForObject(
                "select count(*) from catalog_products where id = :id and status = 'ACTIVE'",
                Map.of("id", productId),
                Long.class);
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден");
        }
        jdbc.update(
                """
                insert into customer_favorites(owner_id, owner_type, product_id, created_at)
                values (:id, :type, :product, :now) on conflict do nothing
                """,
                new MapSqlParameterSource(owner.parameters())
                        .addValue("product", productId)
                        .addValue("now", Timestamp.from(clock.instant())));
    }

    void removeFavorite(ShoppingOwner owner, UUID productId) {
        jdbc.update(
                "delete from customer_favorites where owner_id = :id and owner_type = :type and product_id = :product",
                new MapSqlParameterSource(owner.parameters()).addValue("product", productId));
    }

    @Transactional
    Cart cart(ShoppingOwner owner) {
        var cart = findCart(owner).orElseGet(() -> createCart(owner));
        touchCart(cart.id(), owner);
        return loadCart(cart.id(), cart.version());
    }

    @Transactional
    Cart setItem(ShoppingOwner owner, UUID variantId, int quantity, long expectedVersion) {
        var cart = lockCart(owner);
        requireVersion(cart, expectedVersion);
        var variants = jdbc.query(
                """
                select v.id, p.price_minor from catalog_product_variants v
                join catalog_products p on p.id = v.product_id
                where v.id = :id and v.status = 'ACTIVE' and p.status = 'ACTIVE' and p.price_minor is not null
                """,
                Map.of("id", variantId),
                (result, row) -> new Variant(result.getObject("id", UUID.class), result.getLong("price_minor")));
        if (variants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Вариант товара недоступен");
        }
        var now = Timestamp.from(clock.instant());
        jdbc.update(
                """
                insert into customer_cart_items(
                    cart_id, variant_id, quantity, observed_price_minor, added_at, updated_at)
                values (:cart, :variant, :quantity, :price, :now, :now)
                on conflict (cart_id, variant_id) do update
                set quantity = excluded.quantity,
                    observed_price_minor = excluded.observed_price_minor,
                    updated_at = excluded.updated_at
                """,
                Map.of(
                        "cart",
                        cart.id(),
                        "variant",
                        variantId,
                        "quantity",
                        quantity,
                        "price",
                        variants.getFirst().price(),
                        "now",
                        now));
        return bumped(cart, owner);
    }

    @Transactional
    Cart removeItem(ShoppingOwner owner, UUID variantId, long expectedVersion) {
        var cart = lockCart(owner);
        requireVersion(cart, expectedVersion);
        jdbc.update(
                "delete from customer_cart_items where cart_id = :cart and variant_id = :variant",
                Map.of("cart", cart.id(), "variant", variantId));
        return bumped(cart, owner);
    }

    @Transactional
    void clear(ShoppingOwner owner, long expectedVersion) {
        var cart = lockCart(owner);
        requireVersion(cart, expectedVersion);
        jdbc.update("delete from customer_cart_items where cart_id = :cart", Map.of("cart", cart.id()));
        bump(cart, owner);
    }

    @Transactional
    void mergeGuestIntoCustomer(@Nullable String rawToken, UUID customerId) {
        if (rawToken == null) {
            return;
        }
        var guests = jdbc.query(
                "select id from guest_profiles where token_hash = :hash for update",
                Map.of("hash", hash(rawToken)),
                (result, row) -> result.getObject("id", UUID.class));
        if (guests.isEmpty()) {
            return;
        }
        var guestId = guests.getFirst();
        jdbc.update("""
                insert into customer_favorites(owner_id, owner_type, product_id, created_at)
                select :customer, 'CUSTOMER', product_id, min(created_at)
                from customer_favorites where owner_type = 'GUEST' and owner_id = :guest group by product_id
                on conflict do nothing
                """, Map.of("customer", customerId, "guest", guestId));
        var guestCart = findCart(new ShoppingOwner("GUEST", guestId));
        if (guestCart.isPresent()) {
            var customer = new ShoppingOwner("CUSTOMER", customerId);
            var customerCart = findCart(customer).orElseGet(() -> createCart(customer));
            jdbc.update(
                    """
                    insert into customer_cart_items(
                        cart_id, variant_id, quantity, observed_price_minor, added_at, updated_at)
                    select :target, variant_id, quantity, observed_price_minor, added_at, updated_at
                    from customer_cart_items where cart_id = :source
                    on conflict (cart_id, variant_id) do update
                    set quantity = least(99, customer_cart_items.quantity + excluded.quantity),
                        updated_at = greatest(customer_cart_items.updated_at, excluded.updated_at)
                    """,
                    Map.of(
                            "target",
                            customerCart.id(),
                            "source",
                            guestCart.get().id()));
            bump(customerCart, customer);
        }
        jdbc.update(
                "delete from customer_carts where owner_type = 'GUEST' and owner_id = :guest",
                Map.of("guest", guestId));
        jdbc.update(
                "delete from customer_favorites where owner_type = 'GUEST' and owner_id = :guest",
                Map.of("guest", guestId));
        jdbc.update("delete from guest_profiles where id = :guest", Map.of("guest", guestId));
    }

    int cleanupExpired(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        var expiredCarts = jdbc.query(
                "select id from customer_carts where expires_at < :now order by expires_at, id limit :limit",
                Map.of("now", Timestamp.from(clock.instant()), "limit", batchSize),
                (result, row) -> result.getObject("id", UUID.class));
        var removed = expiredCarts.isEmpty()
                ? 0
                : jdbc.update("delete from customer_carts where id in (:ids)", Map.of("ids", expiredCarts));
        var ids = jdbc.query(
                "select id from guest_profiles where expires_at < :now order by expires_at, id limit :limit",
                Map.of("now", Timestamp.from(clock.instant()), "limit", batchSize),
                (result, row) -> result.getObject("id", UUID.class));
        if (ids.isEmpty()) {
            return removed;
        }
        jdbc.update("delete from customer_carts where owner_type = 'GUEST' and owner_id in (:ids)", Map.of("ids", ids));
        jdbc.update(
                "delete from customer_favorites where owner_type = 'GUEST' and owner_id in (:ids)", Map.of("ids", ids));
        return removed + jdbc.update("delete from guest_profiles where id in (:ids)", Map.of("ids", ids));
    }

    static long parseVersion(String etag) {
        if (!etag.matches("\"v[0-9]+\"")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный If-Match");
        }
        return Long.parseLong(etag.substring(2, etag.length() - 1));
    }

    static String etag(long version) {
        return "\"v" + version + "\"";
    }

    private Cart bumped(CartRecord cart, ShoppingOwner owner) {
        var version = bump(cart, owner);
        return loadCart(cart.id(), version);
    }

    private long bump(CartRecord cart, ShoppingOwner owner) {
        var expiry = clock.instant().plus(owner.customer() ? CUSTOMER_RETENTION : GUEST_RETENTION);
        var parameters = Map.of(
                "id", cart.id(),
                "now", Timestamp.from(clock.instant()),
                "expiry", Timestamp.from(expiry));
        jdbc.update("""
                update customer_carts
                set version = version + 1, updated_at = :now, expires_at = :expiry
                where id = :id
                """, parameters);
        return cart.version() + 1;
    }

    private CartRecord lockCart(ShoppingOwner owner) {
        var cart = findCartForUpdate(owner);
        return cart.orElseGet(() -> createCart(owner));
    }

    private void requireVersion(CartRecord cart, long expected) {
        if (cart.version() != expected) {
            var detail = "Корзина уже изменилась. Обновите страницу";
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, detail);
        }
    }

    private Optional<CartRecord> findCart(ShoppingOwner owner) {
        return queryCart(owner, false);
    }

    private Optional<CartRecord> findCartForUpdate(ShoppingOwner owner) {
        return queryCart(owner, true);
    }

    private Optional<CartRecord> queryCart(ShoppingOwner owner, boolean lock) {
        return jdbc
                .query(
                        "select id, version from customer_carts where owner_type = :type and owner_id = :id"
                                + (lock ? " for update" : ""),
                        owner.parameters(),
                        (result, row) -> new CartRecord(result.getObject("id", UUID.class), result.getLong("version")))
                .stream()
                .findFirst();
    }

    private CartRecord createCart(ShoppingOwner owner) {
        var id = nextId();
        var now = clock.instant();
        var expiry = now.plus(owner.customer() ? CUSTOMER_RETENTION : GUEST_RETENTION);
        jdbc.update(
                """
                insert into customer_carts(id, owner_id, owner_type, created_at, updated_at, expires_at)
                values (:cart, :id, :type, :now, :now, :expiry) on conflict (owner_type, owner_id) do nothing
                """,
                new MapSqlParameterSource(owner.parameters())
                        .addValue("cart", id)
                        .addValue("now", Timestamp.from(now))
                        .addValue("expiry", Timestamp.from(expiry)));
        return findCart(owner).orElseThrow();
    }

    private Cart loadCart(UUID cartId, long version) {
        var items = jdbc.query(
                """
                select i.variant_id, v.product_id, p.canonical_slug, p.name, v.label, i.quantity,
                       coalesce(p.price_minor, i.observed_price_minor, 1) current_price,
                       i.observed_price_minor,
                       (p.status = 'ACTIVE' and v.status = 'ACTIVE' and p.price_minor is not null
                         and coalesce(sum(b.on_hand - b.reserved), 0) >= i.quantity) available
                from customer_cart_items i
                join catalog_product_variants v on v.id = i.variant_id
                join catalog_products p on p.id = v.product_id
                left join inventory_balances b on b.variant_id = v.id
                where i.cart_id = :cart
                group by i.variant_id, v.product_id, p.canonical_slug, p.name, v.label, i.quantity,
                         p.price_minor, i.observed_price_minor, p.status, v.status, i.added_at
                order by i.added_at, i.variant_id
                """,
                Map.of("cart", cartId),
                (result, row) -> new CartLine(
                        result.getObject("variant_id", UUID.class),
                        result.getObject("product_id", UUID.class),
                        Objects.requireNonNull(result.getString("canonical_slug")),
                        Objects.requireNonNull(result.getString("name")),
                        Objects.requireNonNull(result.getString("label")),
                        result.getInt("quantity"),
                        result.getLong("current_price"),
                        result.getObject("observed_price_minor", Long.class),
                        result.getBoolean("available")));
        var notices = items.stream().flatMap(item -> item.notices().stream()).toList();
        var subtotal = items.stream()
                .filter(CartLine::available)
                .mapToLong(item -> item.price() * item.quantity())
                .sum();
        return new Cart(cartId, version, items, subtotal, notices);
    }

    private List<Address> addresses(UUID customerId) {
        return jdbc.query(
                """
                select id, label, recipient_name, phone, postal_code, city, street, apartment
                from customer_addresses where customer_id = :id order by created_at, id
                """,
                Map.of("id", customerId),
                (result, row) -> new Address(
                        result.getObject("id", UUID.class), Objects.requireNonNull(result.getString("label")),
                        Objects.requireNonNull(result.getString("recipient_name")),
                                Objects.requireNonNull(result.getString("phone")),
                        Objects.requireNonNull(result.getString("postal_code")),
                                Objects.requireNonNull(result.getString("city")),
                        Objects.requireNonNull(result.getString("street")), result.getString("apartment")));
    }

    private ShoppingOwner createGuest(HttpServletResponse response) {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var id = nextId();
        var now = clock.instant();
        jdbc.update(
                """
                insert into guest_profiles(id, token_hash, created_at, last_seen_at, expires_at)
                values (:id, :hash, :now, :now, :expiry)
                """,
                Map.of(
                        "id",
                        id,
                        "hash",
                        hash(token),
                        "now",
                        Timestamp.from(now),
                        "expiry",
                        Timestamp.from(now.plus(GUEST_RETENTION))));
        var cookie = new Cookie(GUEST_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge(Math.toIntExact(GUEST_RETENTION.toSeconds()));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        return new ShoppingOwner("GUEST", id);
    }

    private void touchGuest(UUID id) {
        var now = clock.instant();
        jdbc.update(
                "update guest_profiles set last_seen_at = :now, expires_at = :expiry where id = :id",
                Map.of("id", id, "now", Timestamp.from(now), "expiry", Timestamp.from(now.plus(GUEST_RETENTION))));
    }

    private void touchCart(UUID id, ShoppingOwner owner) {
        var now = clock.instant();
        jdbc.update(
                "update customer_carts set updated_at = :now, expires_at = :expiry where id = :id",
                Map.of(
                        "id",
                        id,
                        "now",
                        Timestamp.from(now),
                        "expiry",
                        Timestamp.from(now.plus(owner.customer() ? CUSTOMER_RETENTION : GUEST_RETENTION))));
    }

    private UUID nextId() {
        return Objects.requireNonNull(jdbc.getJdbcTemplate().queryForObject("select uuidv7()", UUID.class));
    }

    private static Optional<UUID> customerId(HttpServletRequest request) {
        var value = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(CustomerPhoneAuthentication.CUSTOMER_ID_SESSION_ATTRIBUTE);
        return value instanceof String id ? Optional.of(UUID.fromString(id)) : Optional.empty();
    }

    private static UUID requireCustomer(HttpServletRequest request) {
        return customerId(request).orElseThrow(CustomerShoppingService::unauthorized);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите в аккаунт");
    }

    static @Nullable String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    record ShoppingOwner(String type, UUID id) {
        boolean customer() {
            return "CUSTOMER".equals(type);
        }

        Map<String, Object> parameters() {
            return Map.of("type", type, "id", id);
        }
    }

    record Context(boolean authenticated, @Nullable UUID customerId, Set<UUID> favorites) {}

    record Profile(
            UUID id,
            String phone,
            @Nullable String displayName,
            @Nullable String email,
            boolean emailVerified,
            List<Address> addresses) {}

    record Address(
            UUID id,
            String label,
            String recipientName,
            String phone,
            String postalCode,
            String city,
            String street,
            @Nullable String apartment) {}

    record AddressDraft(
            String label,
            String recipientName,
            String phone,
            String postalCode,
            String city,
            String street,
            @Nullable String apartment) {}

    record Cart(UUID id, long version, List<CartLine> items, long subtotal, List<Notice> notices) {}

    record CartLine(
            UUID variantId,
            UUID productId,
            String slug,
            String name,
            String label,
            int quantity,
            long price,
            @Nullable Long observedPrice,
            boolean available) {
        List<Notice> notices() {
            var result = new java.util.ArrayList<Notice>();
            if (!available) {
                result.add(new Notice(variantId, "OUT_OF_STOCK", "Товар временно недоступен"));
            }
            if (observedPrice != null && observedPrice != price) {
                result.add(new Notice(variantId, "PRICE_CHANGED", "Цена товара изменилась"));
            }
            return List.copyOf(result);
        }
    }

    record Notice(UUID variantId, String code, String message) {}

    private record CartRecord(UUID id, long version) {}

    private record Variant(UUID id, long price) {}
}
