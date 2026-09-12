CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    public_number VARCHAR(32) NOT NULL UNIQUE,
    owner_type VARCHAR(10) NOT NULL,
    owner_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    reservation_id UUID NOT NULL UNIQUE REFERENCES inventory_reservations(id),
    guest_access_token_hash CHAR(64),
    currency CHAR(3) NOT NULL,
    subtotal_minor BIGINT NOT NULL,
    discount_minor BIGINT NOT NULL,
    total_minor BIGINT NOT NULL,
    email VARCHAR(320) NOT NULL,
    recipient_name VARCHAR(160) NOT NULL,
    phone VARCHAR(16) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    city VARCHAR(120) NOT NULL,
    street VARCHAR(240) NOT NULL,
    apartment VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_orders_owner CHECK (owner_type IN ('GUEST', 'CUSTOMER')),
    CONSTRAINT ck_customer_orders_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_customer_orders_currency CHECK (currency = 'RUB'),
    CONSTRAINT ck_customer_orders_totals CHECK (
        subtotal_minor >= 0 AND discount_minor >= 0 AND total_minor >= 0
        AND subtotal_minor - discount_minor = total_minor),
    CONSTRAINT ck_customer_orders_guest_token CHECK (
        (owner_type = 'GUEST' AND guest_access_token_hash IS NOT NULL)
        OR (owner_type = 'CUSTOMER' AND guest_access_token_hash IS NULL))
);

CREATE INDEX ix_customer_orders_owner ON customer_orders(owner_type, owner_id, created_at DESC);

CREATE TABLE customer_order_lines (
    order_id UUID NOT NULL REFERENCES customer_orders(id) ON DELETE RESTRICT,
    line_number INTEGER NOT NULL,
    variant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(180) NOT NULL,
    variant_label VARCHAR(120) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    discount_minor BIGINT NOT NULL,
    total_minor BIGINT NOT NULL,
    promotion_name VARCHAR(160),
    PRIMARY KEY(order_id, line_number),
    CONSTRAINT ck_customer_order_lines_values CHECK (
        line_number > 0 AND quantity > 0 AND unit_price_minor > 0
        AND discount_minor >= 0 AND total_minor >= 0
        AND unit_price_minor * quantity - discount_minor = total_minor)
);

CREATE TABLE ordering_checkout_commands (
    owner_type VARCHAR(10) NOT NULL,
    owner_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES customer_orders(id),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(owner_type, owner_id, idempotency_key)
);

CREATE TABLE ordering_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(id),
    event_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX ix_ordering_events_order ON ordering_events(order_id, occurred_at, id);

CREATE TABLE transactional_outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX ix_transactional_outbox_unpublished
    ON transactional_outbox(occurred_at, id) WHERE published_at IS NULL;
