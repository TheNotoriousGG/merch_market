CREATE TABLE customer_accounts (
    id UUID PRIMARY KEY,
    phone VARCHAR(16) NOT NULL,
    display_name VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_customer_accounts_phone UNIQUE (phone),
    CONSTRAINT ck_customer_accounts_phone CHECK (phone ~ '^\+[1-9][0-9]{7,14}$')
);

CREATE TABLE customer_phone_challenges (
    id UUID PRIMARY KEY,
    phone VARCHAR(16) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_phone_challenges_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_customer_phone_challenges_phone_created
    ON customer_phone_challenges (phone, created_at DESC);
