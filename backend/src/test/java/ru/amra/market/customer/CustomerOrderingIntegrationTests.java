package ru.amra.market.customer;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerOrderingIntegrationTests extends PostgreSqlIntegrationTest {
    private static final String CSRF = "ordering-csrf-token-123456";
    private static final String KEY = "checkout-key-123456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID variantId;

    @BeforeEach
    void createSellableVariant() {
        var categoryId = uuidV7();
        var productId = uuidV7();
        variantId = uuidV7();
        jdbc.update("""
                insert into catalog_categories(id, slug, name, display_order, status)
                values (?, ?, 'Test', 0, 'ACTIVE')
                """, categoryId, "order-" + categoryId);
        jdbc.update("""
                insert into catalog_products(
                    id, canonical_slug, name, short_description, description, status,
                    primary_category_id, published_at, price_minor)
                values (?, ?, 'Футболка', 'Описание', 'Описание', 'ACTIVE', ?, current_timestamp, 250000)
                """, productId, "order-product-" + productId, categoryId);
        jdbc.update(
                """
                insert into catalog_product_variants(
                    id, product_id, sku, label, status, display_order, defining_signature)
                values (?, ?, ?, 'M', 'ACTIVE', 0, 'size=M')
                """,
                variantId,
                productId,
                "ORDER-" + productId.toString().substring(0, 8).toUpperCase(Locale.ROOT));
        var warehouse = requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class));
        jdbc.update(
                "insert into inventory_balances(warehouse_id, variant_id, on_hand) values (?, ?, 5)",
                warehouse,
                variantId);
        jdbc.update("""
                insert into inventory_movements(
                    warehouse_id, variant_id, movement_type, quantity_delta, reason, reference)
                values (?, ?, 'RECEIPT', 5, 'Ordering test stock', 'ordering-test')
                """, warehouse, variantId);
    }

    @Test
    void guestCheckoutIsAtomicIdempotentAndReadableByScopedToken() throws Exception {
        var guest = guestCookie();
        addTwoItems(guest);

        var first = checkout(guest, "buyer@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalMinor").value(500000))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.guestAccessToken").isNotEmpty())
                .andReturn();
        var json = first.getResponse().getContentAsString();
        var publicNumber = JsonPath.<String>read(json, "$.publicNumber");
        var token = JsonPath.<String>read(json, "$.guestAccessToken");

        checkout(guest, "buyer@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicNumber").value(publicNumber));
        mockMvc.perform(get("/api/v1/customer/orders/{number}", publicNumber).header("X-Guest-Order-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("buyer@example.com"));

        assertThat(jdbc.queryForObject("select count(*) from customer_orders", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from transactional_outbox", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select on_hand from inventory_balances where variant_id = ?", Long.class, variantId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from customer_cart_items", Long.class))
                .isZero();
    }

    @Test
    void sameKeyWithDifferentCheckoutIsRejected() throws Exception {
        var guest = guestCookie();
        addTwoItems(guest);
        checkout(guest, "first@example.com").andExpect(status().isCreated());
        checkout(guest, "other@example.com").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from customer_orders", Long.class))
                .isEqualTo(1);
    }

    private ResultActions checkout(Cookie guest, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/customer/checkout")
                .cookie(guest)
                .with(csrf())
                .header("X-AMRA-CSRF", CSRF)
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkoutBody(email)));
    }

    private Cookie guestCookie() throws Exception {
        return requireNonNull(mockMvc.perform(get("/api/v1/customer/context"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("AMRA_GUEST"));
    }

    private void addTwoItems(Cookie guest) throws Exception {
        mockMvc.perform(get("/api/v1/customer/cart").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/customer/cart/items/{id}", variantId)
                        .cookie(guest)
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF)
                        .header("If-Match", "\"v0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());
    }

    private static String checkoutBody(String email) {
        return """
                {"cartVersion":1,"email":"%s","recipientName":"Иван Иванов",
                 "phone":"+79991234567","postalCode":"354000","city":"Сочи",
                 "street":"ул. Навагинская, 1","apartment":"10"}
                """.formatted(email);
    }

    private UUID uuidV7() {
        return requireNonNull(jdbc.queryForObject("select uuidv7()", UUID.class));
    }
}
