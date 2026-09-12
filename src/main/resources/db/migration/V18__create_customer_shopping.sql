ALTER TABLE customer_accounts
    ADD COLUMN email VARCHAR(320),
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer_accounts(id) ON DELETE CASCADE,
    label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(160) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    city VARCHAR(120) NOT NULL,
    street VARCHAR(240) NOT NULL,
    apartment VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_addresses_phone CHECK (phone ~ '^\+[1-9][0-9]{7,14}$')
);

CREATE INDEX ix_customer_addresses_customer ON customer_addresses(customer_id, created_at, id);

CREATE TABLE guest_profiles (
    id UUID PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_guest_profiles_expiry ON guest_profiles(expires_at, id);

CREATE TABLE customer_favorites (
    owner_id UUID NOT NULL,
    owner_type VARCHAR(10) NOT NULL,
    product_id UUID NOT NULL REFERENCES catalog_products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(owner_type, owner_id, product_id),
    CONSTRAINT ck_customer_favorites_owner_type CHECK (owner_type IN ('GUEST', 'CUSTOMER'))
);

CREATE INDEX ix_customer_favorites_owner ON customer_favorites(owner_type, owner_id, created_at, product_id);

CREATE TABLE customer_carts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    owner_type VARCHAR(10) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_customer_carts_owner UNIQUE(owner_type, owner_id),
    CONSTRAINT ck_customer_carts_owner_type CHECK (owner_type IN ('GUEST', 'CUSTOMER')),
    CONSTRAINT ck_customer_carts_version CHECK (version >= 0)
);

CREATE INDEX ix_customer_carts_expiry ON customer_carts(expires_at, id);

CREATE TABLE customer_cart_items (
    cart_id UUID NOT NULL REFERENCES customer_carts(id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES catalog_product_variants(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL,
    observed_price_minor BIGINT,
    added_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(cart_id, variant_id),
    CONSTRAINT ck_customer_cart_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_customer_cart_items_price CHECK (observed_price_minor IS NULL OR observed_price_minor > 0)
);
