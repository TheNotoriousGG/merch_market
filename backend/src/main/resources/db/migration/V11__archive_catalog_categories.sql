ALTER TABLE catalog_categories DROP CONSTRAINT ck_catalog_categories__status;
ALTER TABLE catalog_categories
    ADD CONSTRAINT ck_catalog_categories__status CHECK (status IN ('ACTIVE', 'HIDDEN', 'ARCHIVED'));
