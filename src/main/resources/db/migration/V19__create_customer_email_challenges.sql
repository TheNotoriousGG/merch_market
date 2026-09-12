CREATE TABLE customer_email_challenges (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer_accounts(id) ON DELETE CASCADE,
    email VARCHAR(320) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_email_challenges_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_customer_email_challenges_customer_created
    ON customer_email_challenges(customer_id, created_at DESC);
