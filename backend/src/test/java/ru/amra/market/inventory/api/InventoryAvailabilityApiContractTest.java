package ru.amra.market.inventory.api;

import static java.util.Objects.requireNonNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.amra.market.inventory.application.ManageInventoryStock;
import ru.amra.market.inventory.application.ReceiveStockCommand;
import ru.amra.market.inventory.domain.CatalogVariantId;
import ru.amra.market.inventory.domain.MovementReason;
import ru.amra.market.inventory.domain.StockQuantity;
import ru.amra.market.inventory.domain.WarehouseId;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryAvailabilityApiContractTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ManageInventoryStock stock;

    @Autowired
    private DataSource dataSource;

    @Test
    void anonymousBatchPreservesFirstSeenOrderAndNeverExposesExactQuantities() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        var warehouse = new WarehouseId(requireNonNull(
                jdbc.queryForObject("select id from inventory_warehouses where code = 'PRIMARY'", UUID.class)));
        var inStock = activeVariant(jdbc, "PUBLIC-IN");
        var noBalance = activeVariant(jdbc, "PUBLIC-ZERO");
        stock.receive(new ReceiveStockCommand(
                warehouse, inStock, new StockQuantity(3), new MovementReason("Public availability fixture"), null));

        mvc.perform(get("/api/v1/inventory/availability")
                        .queryParam("variantId", inStock.value().toString())
                        .queryParam("variantId", noBalance.value().toString())
                        .queryParam("variantId", inStock.value().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(
                        jsonPath("$.items[0].variantId").value(inStock.value().toString()))
                .andExpect(jsonPath("$.items[0].status").value("IN_STOCK"))
                .andExpect(
                        jsonPath("$.items[1].variantId").value(noBalance.value().toString()))
                .andExpect(jsonPath("$.items[1].status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.items[0].quantity").doesNotExist())
                .andExpect(jsonPath("$.items[0].onHand").doesNotExist())
                .andExpect(jsonPath("$.items[0].reserved").doesNotExist());
    }

    private static CatalogVariantId activeVariant(JdbcTemplate jdbc, String suffix) {
        var slug = suffix.toLowerCase(Locale.ROOT);
        var category =
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_categories (id, slug, name, display_order, status)
                values (uuidv7(), ?, ?, 0, 'ACTIVE') returning id
                """, UUID.class, "availability-" + slug, "Availability " + suffix));
        var product = requireNonNull(
                jdbc.queryForObject("""
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description,
                    status, primary_category_id, published_at
                ) values (uuidv7(), ?, ?, 'fixture', 'fixture', 'ACTIVE', ?, current_timestamp)
                returning id
                """, UUID.class, "availability-product-" + slug, "Product " + suffix, category));
        return new CatalogVariantId(
                requireNonNull(jdbc.queryForObject("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature
                ) values (uuidv7(), ?, ?, 'Fixture', 'ACTIVE', 0, ?)
                returning id
                """, UUID.class, product, "AVL-" + suffix, "fixture=" + suffix)));
    }
}
