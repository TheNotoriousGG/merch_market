package ru.amra.market.inventory.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventoryAdministrationApiTest extends PostgreSqlIntegrationTest {
    private static final String CSRF = "inventory-csrf-token";
    private static final String TRACE_ID = "inventory-admin-trace-0001";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void receivesReadsReconcilesAndExactlyReplaysOriginalResult() throws Exception {
        var variant = activeVariant(new JdbcTemplate(dataSource), "ADMIN-FLOW");

        var first = unsafe(receipt(variant, "receipt-key-0001", 5, "Initial delivery", null))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andExpect(jsonPath("$.balance.onHand").value(5))
                .andExpect(jsonPath("$.balance.reserved").value(0))
                .andExpect(jsonPath("$.balance.available").value(5))
                .andExpect(jsonPath("$.movement.type").value("RECEIPT"))
                .andReturn();

        unsafe(receipt(variant, "receipt-key-0002", 2, "Second delivery", "ASN-2"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""));

        var replay = unsafe(receipt(variant, "receipt-key-0001", 5, "Initial delivery", null))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v1\""))
                .andReturn();
        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());

        mvc.perform(get("/api/v1/admin/inventory/balances/{variantId}", variant).with(warehouseManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v2\""))
                .andExpect(jsonPath("$.onHand").value(7));

        unsafe(adjustment(variant, "\"v1\"", "adjust-key-stale-0001", 4))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("STALE_RESOURCE_VERSION"));
        unsafe(adjustment(variant, "\"v2\"", "adjust-key-success-0001", 4))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"v3\""))
                .andExpect(jsonPath("$.balance.onHand").value(4))
                .andExpect(jsonPath("$.movement.type").value("ADJUSTMENT_DECREASE"));

        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                        "select count(*) from inventory_audit_events where variant_id = ?", Integer.class, variant))
                .isEqualTo(3);
        assertThat(jdbc.queryForList("""
                        select actor_scope, action, correlation_id,
                               safe_diff ->> 'fromOnHand' as from_on_hand,
                               safe_diff ->> 'toOnHand' as to_on_hand,
                               safe_diff ->> 'fromVersion' as from_version,
                               safe_diff ->> 'toVersion' as to_version
                        from inventory_audit_events where variant_id = ? order by occurred_at, id
                        """, variant))
                .extracting(
                        row -> row.get("actor_scope"),
                        row -> row.get("action"),
                        row -> row.get("correlation_id"),
                        row -> row.get("from_on_hand"),
                        row -> row.get("to_on_hand"),
                        row -> row.get("from_version"),
                        row -> row.get("to_version"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "warehouse-manager-1", "STOCK_RECEIVED", TRACE_ID, "0", "5", "0", "1"),
                        org.assertj.core.groups.Tuple.tuple(
                                "warehouse-manager-1", "STOCK_RECEIVED", TRACE_ID, "5", "7", "1", "2"),
                        org.assertj.core.groups.Tuple.tuple(
                                "warehouse-manager-1", "STOCK_RECONCILED", TRACE_ID, "7", "4", "2", "3"));
        assertThat(jdbc.queryForList("""
                        select distinct key from inventory_audit_events,
                        lateral jsonb_object_keys(safe_diff) key where variant_id = ? order by key
                        """, String.class, variant))
                .containsExactly(
                        "fromAvailable",
                        "fromOnHand",
                        "fromReserved",
                        "fromVersion",
                        "toAvailable",
                        "toOnHand",
                        "toReserved",
                        "toVersion");
    }

    @Test
    void rejectsIdempotencyConflictMissingPreconditionAndUnknownOrInactiveVariant() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        var active = activeVariant(jdbc, "ADMIN-ERRORS");
        unsafe(receipt(active, "receipt-key-conflict", 3, "Accepted delivery", "ASN-1"))
                .andExpect(status().isOk());
        unsafe(receipt(active, "receipt-key-conflict", 4, "Different delivery", "ASN-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_IDEMPOTENCY_CONFLICT"));

        unsafe(post("/api/v1/admin/inventory/balances/{variantId}/adjustments", active)
                        .header("Idempotency-Key", "adjust-missing-etag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onHand\":2,\"reason\":\"Counted stock\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        mvc.perform(get("/api/v1/admin/inventory/balances/{variantId}", UUID.randomUUID())
                        .with(warehouseManager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVENTORY_BALANCE_NOT_FOUND"));

        unsafe(receipt(UUID.randomUUID(), "receipt-inactive-variant", 1, "Unknown product", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVENTORY_VARIANT_NOT_ACTIVE"));
    }

    @Test
    void enforcesRoleVerifiedIdentityMfaAndCsrf() throws Exception {
        var request = receipt(UUID.randomUUID(), "receipt-security-key", 1, "Security check", null);
        mvc.perform(request.with(csrf()).header("X-AMRA-CSRF", CSRF)).andExpect(status().isUnauthorized());
        mvc.perform(receipt(UUID.randomUUID(), "receipt-security-key", 1, "Security check", null)
                        .with(customer())
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF))
                .andExpect(status().isForbidden());
        mvc.perform(receipt(UUID.randomUUID(), "receipt-security-key", 1, "Security check", null)
                        .with(warehouseManagerWithoutMfa())
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF))
                .andExpect(status().isForbidden());
        mvc.perform(receipt(UUID.randomUUID(), "receipt-security-key", 1, "Security check", null)
                        .with(unverifiedWarehouseManager())
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF))
                .andExpect(status().isForbidden());
        mvc.perform(receipt(UUID.randomUUID(), "receipt-security-key", 1, "Security check", null)
                        .with(warehouseManager())
                        .header("X-AMRA-CSRF", CSRF))
                .andExpect(status().isForbidden());

        var adminVariant = activeVariant(new JdbcTemplate(dataSource), "ADMIN-ROLE");
        mvc.perform(receipt(adminVariant, "receipt-admin-role-key", 1, "Admin authorization", null)
                        .with(admin())
                        .with(csrf())
                        .header("X-AMRA-CSRF", CSRF)
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk());
    }

    @Test
    void listsCatalogVariantsWithZeroBalancesAndRecentMovements() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        var stocked = activeVariant(jdbc, "OVERVIEW-STOCKED");
        var empty = activeVariant(jdbc, "OVERVIEW-EMPTY");
        unsafe(receipt(stocked, "overview-receipt-key", 6, "Free receipt", "DOC-42"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/inventory").with(warehouseManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.variantId == '%s')].onHand".formatted(stocked))
                        .value(6))
                .andExpect(jsonPath("$.items[?(@.variantId == '%s')].available".formatted(empty))
                        .value(0))
                .andExpect(jsonPath("$.movements[?(@.variantId == '%s')].type".formatted(stocked))
                        .value("RECEIPT"))
                .andExpect(jsonPath("$.movements[?(@.variantId == '%s')].reference".formatted(stocked))
                        .value("DOC-42"));
    }

    @Test
    void receivesStockForDraftCatalogCard() throws Exception {
        var variant = draftVariant(new JdbcTemplate(dataSource), "DRAFT-RECEIPT");
        unsafe(receipt(variant, "draft-receipt-key", 4, "Received before publication", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.onHand").value(4));
    }

    private org.springframework.test.web.servlet.ResultActions unsafe(MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.with(warehouseManager())
                .with(csrf())
                .header("X-AMRA-CSRF", CSRF)
                .header("X-Trace-Id", TRACE_ID));
    }

    private static MockHttpServletRequestBuilder receipt(
            UUID variant, String key, long quantity, String reason, @Nullable String reference) {
        var referenceJson = reference == null ? "" : ",\"reference\":\"" + reference + "\"";
        return post("/api/v1/admin/inventory/balances/{variantId}/receipts", variant)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":" + quantity + ",\"reason\":\"" + reason + "\"" + referenceJson + "}");
    }

    private static MockHttpServletRequestBuilder adjustment(UUID variant, String etag, String key, long onHand) {
        return post("/api/v1/admin/inventory/balances/{variantId}/adjustments", variant)
                .header("If-Match", etag)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"onHand\":" + onHand + ",\"reason\":\"Cycle count\"}");
    }

    private static RequestPostProcessor warehouseManager() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"))
                .idToken(token -> token.subject("warehouse-manager-1")
                        .claim("email_verified", true)
                        .claim("acr", "2"));
    }

    private static RequestPostProcessor admin() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .idToken(token ->
                        token.subject("admin-1").claim("email_verified", true).claim("acr", "2"));
    }

    private static RequestPostProcessor customer() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                .idToken(token -> token.claim("email_verified", true).claim("acr", "2"));
    }

    private static RequestPostProcessor warehouseManagerWithoutMfa() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"))
                .idToken(token -> token.claim("email_verified", true));
    }

    private static RequestPostProcessor unverifiedWarehouseManager() {
        return oidcLogin()
                .authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"))
                .idToken(token -> token.claim("email_verified", false).claim("acr", "2"));
    }

    private static UUID activeVariant(JdbcTemplate jdbc, String suffix) {
        var normalized = suffix.toLowerCase(Locale.ROOT);
        var category =
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_categories (id, slug, name, display_order, status)
                values (uuidv7(), ?, ?, 0, 'ACTIVE') returning id
                """, UUID.class, "warehouse-" + normalized, "Warehouse " + suffix));
        var product = requireNonNull(
                jdbc.queryForObject("""
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description,
                    status, primary_category_id, published_at
                ) values (uuidv7(), ?, ?, 'fixture', 'fixture', 'ACTIVE', ?, current_timestamp)
                returning id
                """, UUID.class, "warehouse-product-" + normalized, "Product " + suffix, category));
        return requireNonNull(jdbc.queryForObject("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature
                ) values (uuidv7(), ?, ?, 'Fixture', 'ACTIVE', 0, ?)
                returning id
                """, UUID.class, product, "WH-" + suffix, "fixture=" + suffix));
    }

    private static UUID draftVariant(JdbcTemplate jdbc, String suffix) {
        var normalized = suffix.toLowerCase(Locale.ROOT);
        var category =
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_categories (id, slug, name, display_order, status)
                values (uuidv7(), ?, ?, 0, 'ACTIVE') returning id
                """, UUID.class, "warehouse-" + normalized, "Warehouse " + suffix));
        var product = requireNonNull(
                jdbc.queryForObject("""
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description,
                    status, primary_category_id, published_at
                ) values (uuidv7(), ?, ?, 'fixture', 'fixture', 'DRAFT', ?, null)
                returning id
                """, UUID.class, "warehouse-product-" + normalized, "Product " + suffix, category));
        return requireNonNull(jdbc.queryForObject("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature
                ) values (uuidv7(), ?, ?, 'Fixture', 'ACTIVE', 0, ?)
                returning id
                """, UUID.class, product, "WH-" + suffix, "fixture=" + suffix));
    }
}
