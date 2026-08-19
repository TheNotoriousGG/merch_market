ALTER TABLE catalog_product_characteristics
    ADD COLUMN value_label VARCHAR(160),
    ADD COLUMN color_hex VARCHAR(7);

UPDATE catalog_product_characteristics
SET value_label = attribute_value;

ALTER TABLE catalog_product_characteristics
    ALTER COLUMN value_label SET NOT NULL,
    ADD CONSTRAINT ck_catalog_product_characteristics__label_not_blank
        CHECK (length(btrim(value_label)) BETWEEN 1 AND 160),
    ADD CONSTRAINT ck_catalog_product_characteristics__color_hex
        CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$');

ALTER TABLE catalog_variant_attribute_values
    ADD COLUMN value_label VARCHAR(160),
    ADD COLUMN color_hex VARCHAR(7);

UPDATE catalog_variant_attribute_values
SET value_label = attribute_value;

ALTER TABLE catalog_variant_attribute_values
    ALTER COLUMN value_label SET NOT NULL,
    ADD CONSTRAINT ck_catalog_variant_attribute_values__label_not_blank
        CHECK (length(btrim(value_label)) BETWEEN 1 AND 160),
    ADD CONSTRAINT ck_catalog_variant_attribute_values__color_hex
        CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$');
