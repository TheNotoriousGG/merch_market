CREATE TABLE pricing_base_price_periods (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL REFERENCES catalog_product_variants(id) ON DELETE CASCADE,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'RUB',
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pricing_base_price_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_pricing_base_price_currency CHECK (currency = 'RUB'),
    CONSTRAINT ck_pricing_base_price_period CHECK (ends_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX ix_pricing_base_price_lookup
    ON pricing_base_price_periods(variant_id, starts_at DESC, ends_at);

CREATE FUNCTION pricing_reject_overlapping_base_prices() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.variant_id::text, 0));
    IF EXISTS (
        SELECT 1 FROM pricing_base_price_periods period
        WHERE period.variant_id = NEW.variant_id
          AND period.id <> NEW.id
          AND period.starts_at < COALESCE(NEW.ends_at, 'infinity'::timestamptz)
          AND COALESCE(period.ends_at, 'infinity'::timestamptz) > NEW.starts_at
    ) THEN
        RAISE EXCEPTION 'overlapping base price period for variant %', NEW.variant_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_pricing_base_price_no_overlap
BEFORE INSERT OR UPDATE ON pricing_base_price_periods
FOR EACH ROW EXECUTE FUNCTION pricing_reject_overlapping_base_prices();

CREATE TABLE pricing_promotions (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    promotion_type VARCHAR(20) NOT NULL,
    percent INTEGER,
    amount_minor BIGINT,
    paid_quantity INTEGER,
    bundle_quantity INTEGER,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pricing_promotion_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_pricing_promotion_payload CHECK (
        (promotion_type = 'PERCENT' AND percent BETWEEN 1 AND 90
            AND amount_minor IS NULL AND paid_quantity IS NULL AND bundle_quantity IS NULL)
        OR (promotion_type = 'FIXED_LINE' AND amount_minor > 0
            AND percent IS NULL AND paid_quantity IS NULL AND bundle_quantity IS NULL)
        OR (promotion_type = 'MULTI_BUY' AND paid_quantity > 0 AND bundle_quantity > paid_quantity
            AND percent IS NULL AND amount_minor IS NULL)
    )
);

CREATE TABLE pricing_promotion_variants (
    promotion_id UUID NOT NULL REFERENCES pricing_promotions(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES catalog_product_variants(id) ON DELETE CASCADE,
    PRIMARY KEY(promotion_id, variant_id)
);

CREATE INDEX ix_pricing_active_promotions
    ON pricing_promotions(starts_at, ends_at) WHERE enabled;
CREATE INDEX ix_pricing_promotion_variant
    ON pricing_promotion_variants(variant_id, promotion_id);

INSERT INTO pricing_base_price_periods(id, variant_id, amount_minor, starts_at)
SELECT uuidv7(), variant.id, product.price_minor, '-infinity'::timestamptz
FROM catalog_product_variants variant
JOIN catalog_products product ON product.id = variant.product_id
WHERE product.price_minor IS NOT NULL;
