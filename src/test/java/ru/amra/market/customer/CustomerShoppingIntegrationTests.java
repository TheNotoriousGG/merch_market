package ru.amra.market.customer;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "amra.customer.phone-auth.expose-development-code=true")
@Transactional
class CustomerShoppingIntegrationTests extends PostgreSqlIntegrationTest {
    private static final String CSRF = "customer-csrf-token-123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CustomerShoppingService shopping;

    private UUID productId;
    private UUID variantId;

    @BeforeEach
    void createSellableProduct() {
        var categoryId = uuidV7();
        productId = uuidV7();
        variantId = uuidV7();
        jdbc.update("""
                insert into catalog_categories(id, slug, name, display_order, status)
                values (?, ?, 'Тест', 0, 'ACTIVE')
                """, categoryId, "test-" + categoryId);
        jdbc.update("""
                insert into catalog_products(
                    id, canonical_slug, name, short_description, description, status,
                    primary_category_id, published_at, price_minor)
                values (
                    ?, ?, 'Футболка', 'Описание', 'Полное описание',
                    'ACTIVE', ?, current_timestamp, 250000)
                """, productId, "shirt-" + productId, categoryId);
        jdbc.update(
                """
                insert into catalog_product_variants(
                    id, product_id, sku, label, status, display_order, defining_signature)
                values (?, ?, ?, 'M / чёрный', 'ACTIVE', 0, ?)
                """,
                variantId,
                productId,
                "SKU-" + productId.toString().substring(0, 8).toUpperCase(Locale.ROOT),
                "size=M");
        var warehouseId = jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class);
        jdbc.update(
                "insert into inventory_balances(warehouse_id, variant_id, on_hand) values (?, ?, 10)",
                warehouseId,
                variantId);
    }

    @Test
    void guestFavoritesAndCartArePersistentAndOptimisticallyLocked() throws Exception {
        var context = mockMvc.perform(get("/api/v1/customer/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andReturn();
        var guest = requireNonNull(context.getResponse().getCookie(CustomerShoppingService.GUEST_COOKIE));
        assertThat(guest.isHttpOnly()).isTrue();

        mockMvc.perform(put("/api/v1/customer/favorites/{id}", productId)
                        .cookie(guest)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/customer/context").cookie(guest))
                .andExpect(jsonPath("$.favoriteProductIds[0]").value(productId.toString()));

        var cart = mockMvc.perform(get("/api/v1/customer/cart").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v0\""))
                .andReturn();
        var csrfCookie = cart.getResponse().getCookie("AMRA_CSRF");
        var updated = mockMvc.perform(put("/api/v1/customer/cart/items/{id}", variantId)
                        .cookie(guest)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.subtotalMinor").value(500000))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andReturn();
        assertThat(csrfCookie == null || updated.getResponse().getStatus() == 200)
                .isTrue();

        mockMvc.perform(put("/api/v1/customer/cart/items/{id}", variantId)
                        .cookie(guest)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void revalidationReportsPriceAndAvailabilityChanges() throws Exception {
        var guest = guestCookie();
        mockMvc.perform(get("/api/v1/customer/cart").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/customer/cart/items/{id}", variantId)
                        .cookie(guest)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());

        jdbc.update("update catalog_products set price_minor = 275000 where id = ?", productId);
        jdbc.update("update inventory_balances set on_hand = 1 where variant_id = ?", variantId);

        mockMvc.perform(get("/api/v1/customer/cart").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotalMinor").value(0))
                .andExpect(jsonPath("$.items[0].unitPriceMinor").value(275000))
                .andExpect(jsonPath("$.notices[?(@.code == 'PRICE_CHANGED')]").exists())
                .andExpect(jsonPath("$.notices[?(@.code == 'OUT_OF_STOCK')]").exists());
    }

    @Test
    void guestStateMergesIntoExistingCustomerWithoutDuplicates() throws Exception {
        var guest = guestCookie();
        mockMvc.perform(put("/api/v1/customer/favorites/{id}", productId)
                        .cookie(guest)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/customer/cart").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/customer/cart/items/{id}", variantId)
                        .cookie(guest)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());

        var customerId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into customer_accounts(id, phone, created_at, updated_at) values (?, '+79990001122', ?, ?)",
                customerId,
                now,
                now);
        shopping.mergeGuestIntoCustomer(guest.getValue(), customerId);
        shopping.mergeGuestIntoCustomer(guest.getValue(), customerId);

        var session = customerSession("+79990001122");
        mockMvc.perform(get("/api/v1/customer/context").cookie(session))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.favoriteProductIds.length()").value(1));
        mockMvc.perform(get("/api/v1/customer/cart").cookie(session))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void profileAddressesAreScopedToAuthenticatedCustomer() throws Exception {
        var first = customerSession("+79990000001");
        var second = customerSession("+79990000002");
        var created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/customer/addresses")
                        .cookie(first)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Дом","recipientName":"Иван","phone":"+79990000001",
                                 "postalCode":"123456","city":"Москва","street":"Тверская, 1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Дом"))
                .andReturn();
        var location = requireNonNull(created.getResponse().getHeader("Location"));
        var id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(delete("/api/v1/customer/addresses/{id}", id)
                        .cookie(second)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/customer/profile").cookie(first))
                .andExpect(jsonPath("$.addresses.length()").value(1));
    }

    @Test
    void rejectsMalformedVersionsAndUnboundedCleanupRequests() {
        assertThat(CustomerShoppingService.parseVersion("\"v42\"")).isEqualTo(42);
        assertThatThrownBy(() -> CustomerShoppingService.parseVersion("42"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> shopping.cleanupExpired(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shopping.cleanupExpired(1001)).isInstanceOf(IllegalArgumentException.class);
        assertThat(shopping.cleanupExpired(10)).isZero();
    }

    private Cookie guestCookie() throws Exception {
        return requireNonNull(mockMvc.perform(get("/api/v1/customer/context"))
                .andReturn()
                .getResponse()
                .getCookie(CustomerShoppingService.GUEST_COOKIE));
    }

    private Cookie customerSession(String phone) throws Exception {
        var started = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/customer/auth/phone/start")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"%s\"}".formatted(phone)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var challengeId = (String) JsonPath.read(started, "$.challengeId");
        var code = (String) JsonPath.read(started, "$.developmentCode");
        return requireNonNull(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/v1/customer/auth/phone/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"%s\",\"code\":\"%s\"}".formatted(challengeId, code)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("SESSION"));
    }

    private UUID uuidV7() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
