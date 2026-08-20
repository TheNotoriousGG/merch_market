ALTER TABLE catalog_products ADD COLUMN price_minor BIGINT;

ALTER TABLE catalog_products
    ADD CONSTRAINT ck_catalog_products__price_positive
        CHECK (price_minor IS NULL OR price_minor > 0);
