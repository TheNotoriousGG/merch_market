CREATE TABLE catalog_command_idempotency (
    actor_scope VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pk_catalog_command_idempotency PRIMARY KEY (actor_scope, idempotency_key),
    CONSTRAINT ck_catalog_command_idempotency__actor_not_blank
        CHECK (length(btrim(actor_scope)) BETWEEN 1 AND 255),
    CONSTRAINT ck_catalog_command_idempotency__key_not_blank
        CHECK (length(btrim(idempotency_key)) BETWEEN 16 AND 128),
    CONSTRAINT ck_catalog_command_idempotency__operation_format
        CHECK (operation ~ '^[A-Z][A-Z0-9_]{1,79}$'),
    CONSTRAINT ck_catalog_command_idempotency__fingerprint_format
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_catalog_command_idempotency__resource_uuid_v7
        CHECK (resource_id IS NULL OR uuid_extract_version(resource_id) = 7),
    CONSTRAINT ck_catalog_command_idempotency__completion_state
        CHECK ((resource_id IS NULL) = (completed_at IS NULL))
);

CREATE INDEX ix_catalog_command_idempotency__created_at
    ON catalog_command_idempotency (created_at);
