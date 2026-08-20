ALTER TABLE catalog_products DROP CONSTRAINT ck_catalog_products__publication_state;
ALTER TABLE catalog_products
    ADD CONSTRAINT ck_catalog_products__publication_state CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR
        (status = 'ACTIVE' AND published_at IS NOT NULL) OR
        status = 'ARCHIVED'
    );
