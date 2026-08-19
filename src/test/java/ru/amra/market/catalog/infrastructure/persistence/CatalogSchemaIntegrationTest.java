package ru.amra.market.catalog.infrastructure.persistence;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.amra.market.testing.PostgreSqlIntegrationTest;

@Transactional
@SpringBootTest
class CatalogSchemaIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsRelationalCatalogTablesAndPurposeBuiltIndexes() {
        assertThat(jdbc.queryForList("""
                        select tablename
                        from pg_catalog.pg_tables
                        where schemaname = 'amra_shop' and tablename like 'catalog_%'
                        order by tablename
                        """, String.class))
                .contains(
                        "catalog_categories",
                        "catalog_products",
                        "catalog_product_variants",
                        "catalog_product_media",
                        "catalog_attribute_definitions",
                        "catalog_variant_attribute_values",
                        "catalog_collections");
        assertThat(jdbc.queryForList("""
                        select indexname
                        from pg_catalog.pg_indexes
                        where schemaname = 'amra_shop'
                        """, String.class))
                .contains(
                        "ix_catalog_products__search_document",
                        "ix_catalog_products__name_trigram",
                        "uq_catalog_product_variants__sku_ci",
                        "uq_catalog_product_media__one_primary");
    }

    @Test
    void enforcesCaseInsensitiveSiblingSlugUniquenessIncludingRoots() {
        insertCategory("odezhda", "Одежда");

        assertThatThrownBy(() -> insertCategory("odezhda", "Другая одежда"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_catalog_categories__parent_slug_ci");
    }

    @Test
    void enforcesGlobalSkuUniqueness() {
        var categoryId = insertCategory("odezhda", "Одежда");
        var firstProductId = insertDraftProduct(categoryId, "first-product");
        var secondProductId = insertDraftProduct(categoryId, "second-product");
        insertVariant(firstProductId, "AMR-GLOBAL-1", "color=BLACK|size=M");

        assertThatThrownBy(() -> insertVariant(secondProductId, "AMR-GLOBAL-1", "color=WHITE|size=L"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_catalog_product_variants__sku_ci");
    }

    @Test
    void enforcesPublicationState() {
        var categoryId = insertCategory("odezhda", "Одежда");
        var firstProductId = insertDraftProduct(categoryId, "first-product");

        assertThatThrownBy(
                        () -> jdbc.update("update catalog_products set status = 'ACTIVE' where id = ?", firstProductId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_catalog_products__publication_state");
    }

    @Test
    void generatedSearchDocumentIndexesPublishedCopy() {
        var categoryId = insertCategory("odezhda", "Одежда");
        var productId = insertDraftProduct(categoryId, "grafitovaya-futbolka");

        assertThat(jdbc.queryForObject(
                        "select search_document @@ plainto_tsquery('russian', 'графитовая') "
                                + "from catalog_products where id = ?",
                        Boolean.class,
                        productId))
                .isTrue();
    }

    private UUID insertCategory(String slug, String name) {
        return requireNonNull(jdbc.queryForObject("""
                insert into catalog_categories (id, slug, name, display_order, status)
                values (uuidv7(), ?, ?, 0, 'ACTIVE')
                returning id
                """, UUID.class, slug, name));
    }

    private UUID insertDraftProduct(UUID categoryId, String slug) {
        return requireNonNull(jdbc.queryForObject("""
                insert into catalog_products (
                    id, canonical_slug, name, short_description, description, status, primary_category_id
                ) values (uuidv7(), ?, 'Графитовая футболка', 'Короткое описание', 'Полное описание', 'DRAFT', ?)
                returning id
                """, UUID.class, slug, categoryId));
    }

    private void insertVariant(UUID productId, String sku, String signature) {
        jdbc.update("""
                insert into catalog_product_variants (
                    id, product_id, sku, label, status, display_order, defining_signature
                ) values (uuidv7(), ?, ?, 'Variant', 'ACTIVE', 0, ?)
                """, productId, sku, signature);
    }
}
