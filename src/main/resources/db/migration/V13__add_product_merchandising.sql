ALTER TABLE catalog_products
    ADD COLUMN new_arrival BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN new_until TIMESTAMPTZ,
    ADD COLUMN on_sale BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sale_percent INTEGER;

ALTER TABLE catalog_products ADD CONSTRAINT catalog_products_merchandising_check CHECK (
    (new_arrival OR new_until IS NULL)
    AND ((on_sale AND sale_percent BETWEEN 1 AND 90) OR (NOT on_sale AND sale_percent IS NULL))
);
